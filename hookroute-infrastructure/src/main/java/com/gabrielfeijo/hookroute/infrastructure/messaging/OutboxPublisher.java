package com.gabrielfeijo.hookroute.infrastructure.messaging;

import com.gabrielfeijo.hookroute.infrastructure.persistence.OutboxMessageRepository;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "hookroute.worker.enabled", havingValue = "true")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxMessageRepository outbox;
    private final StringRedisTemplate redis;
    private final Clock clock;

    public OutboxPublisher(OutboxMessageRepository outbox, StringRedisTemplate redis, Clock clock) {
        this.outbox = outbox;
        this.redis = redis;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hookroute.outbox.poll-delay-ms:200}")
    @Transactional
    public void publishDue() {
        for (var message : outbox.lockDue(clock.instant(), PageRequest.of(0, 50))) {
            try {
                redis.opsForStream().add(StreamRecords.string(Map.of(
                        "messageId", message.id.toString(),
                        "body", message.payload)).withStreamKey(message.streamName));
                message.status = "PUBLISHED";
                message.publishedAt = clock.instant();
                message.lastError = null;
            } catch (RuntimeException error) {
                message.attempts++;
                message.lastError = error.getMessage();
                message.availableAt = clock.instant().plusMillis(Math.min(30_000, 1_000L << Math.min(message.attempts, 5)));
                log.warn("Outbox publication failed messageId={} attempt={}", message.id, message.attempts, error);
            }
        }
    }
}
