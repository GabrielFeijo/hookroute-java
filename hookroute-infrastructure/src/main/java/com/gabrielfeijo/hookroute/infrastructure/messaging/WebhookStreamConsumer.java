package com.gabrielfeijo.hookroute.infrastructure.messaging;

import tools.jackson.databind.ObjectMapper;
import com.gabrielfeijo.hookroute.domain.DeliveryJob;
import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import com.gabrielfeijo.hookroute.infrastructure.service.DeliveryLifecycleService;
import com.gabrielfeijo.hookroute.infrastructure.service.EgressService;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.Range;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hookroute.worker.enabled", havingValue = "true")
public class WebhookStreamConsumer {
    private static final Logger log = LoggerFactory.getLogger(WebhookStreamConsumer.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DeliveryLifecycleService lifecycle;
    private final EgressService egress;
    private final HookRouteProperties.Queue queue;

    public WebhookStreamConsumer(StringRedisTemplate redis, ObjectMapper objectMapper,
                                 DeliveryLifecycleService lifecycle, EgressService egress,
                                 HookRouteProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.lifecycle = lifecycle;
        this.egress = egress;
        this.queue = properties.getQueue();
    }

    @PostConstruct
    void initializeGroup() {
        try {
            redis.opsForStream().createGroup(queue.getMainStream(), ReadOffset.from("0-0"), queue.getConsumerGroup());
        } catch (RedisSystemException error) {
            Throwable root = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(error);
            if (root.getMessage() == null || !root.getMessage().contains("BUSYGROUP")) {
                log.warn("Could not initialize Redis consumer group; it will be retried while polling", error);
            }
        }
    }

    @Scheduled(fixedDelayString = "${hookroute.queue.poll-delay-ms:250}")
    public void poll() {
        try {
            var records = redis.opsForStream().read(
                    Consumer.from(queue.getConsumerGroup(), queue.getConsumerName()),
                    StreamReadOptions.empty().count(queue.getBatchSize()),
                    StreamOffset.create(queue.getMainStream(), ReadOffset.lastConsumed()));
            if (records == null) return;
            for (var record : records) process(record);
        } catch (RuntimeException error) {
            log.warn("Redis stream polling failed", error);
        }
    }

    @Scheduled(fixedDelayString = "${hookroute.queue.recovery-delay-ms:30000}")
    public void recoverAbandonedMessages() {
        try {
            var pending = redis.opsForStream().pending(queue.getMainStream(), queue.getConsumerGroup(), Range.unbounded(),
                    queue.getBatchSize(), Duration.ofMillis(queue.getVisibilityTimeoutMs()));
            if (pending == null || pending.isEmpty()) return;
            var ids = pending.stream().map(message -> message.getId()).toArray(org.springframework.data.redis.connection.stream.RecordId[]::new);
            var claimed = redis.opsForStream().claim(queue.getMainStream(), queue.getConsumerGroup(), queue.getConsumerName(),
                    Duration.ofMillis(queue.getVisibilityTimeoutMs()), ids);
            if (claimed != null) for (var record : claimed) process(record);
        } catch (RuntimeException error) {
            log.warn("Redis pending-message recovery failed", error);
        }
    }

    private void process(MapRecord<String, Object, Object> record) {
        try {
            Object rawBody = record.getValue().get("body");
            var envelope = objectMapper.readTree(String.valueOf(rawBody));
            var job = new DeliveryJob(
                    UUID.fromString(envelope.path("jobId").asText()),
                    UUID.fromString(envelope.path("eventId").asText()),
                    envelope.path("attempt").asInt(),
                    envelope.path("correlationId").asText(null));
            var work = lifecycle.begin(job);
            if (work != null) {
                String destination = egress.destination(work.eventType());
                try {
                    egress.dispatch(work.eventType(), work.payload(), job.jobId().toString(), work.correlationId());
                    lifecycle.delivered(job, destination);
                } catch (RuntimeException error) {
                    lifecycle.failed(job, destination, error);
                }
            }
            acknowledge(record);
        } catch (Exception error) {
            log.error("Malformed or unprocessable Redis message recordId={}", record.getId(), error);
            redis.opsForStream().add(StreamRecords.string(Map.of(
                    "originalRecordId", record.getId().getValue(),
                    "failedReason", error.getMessage() == null ? "Unknown failure" : error.getMessage(),
                    "body", String.valueOf(record.getValue().get("body"))
            )).withStreamKey(queue.getDeadLetterStream()));
            acknowledge(record);
        }
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        redis.opsForStream().acknowledge(queue.getMainStream(), queue.getConsumerGroup(), record.getId());
        redis.opsForStream().delete(queue.getMainStream(), record.getId());
    }
}
