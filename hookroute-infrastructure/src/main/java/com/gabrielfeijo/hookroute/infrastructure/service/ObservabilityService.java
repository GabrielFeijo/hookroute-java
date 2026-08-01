package com.gabrielfeijo.hookroute.infrastructure.service;

import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import com.gabrielfeijo.hookroute.infrastructure.persistence.OutboxMessageRepository;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ObservabilityService {
    private final JdbcTemplate jdbc;
    private final RedisConnectionFactory redis;
    private final WebhookEventRepository events;
    private final OutboxMessageRepository outbox;
    private final HookRouteProperties properties;
    private final Clock clock;

    public ObservabilityService(JdbcTemplate jdbc, RedisConnectionFactory redis, WebhookEventRepository events,
                                OutboxMessageRepository outbox, HookRouteProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.events = events;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    public Map<String, Object> health() {
        Map<String, Object> database = checkDatabase();
        Map<String, Object> queues = checkRedis();
        boolean ok = database.get("status").equals("up") && queues.get("status").equals("up");
        return Map.of("status", ok ? "ok" : "degraded", "checkedAt", clock.instant(),
                "checks", Map.of("database", database, "queues", queues));
    }

    public Map<String, Object> metrics() {
        long queued = events.countByStatus("queued");
        long processing = events.countByStatus("processing");
        long delivered = events.countByStatus("delivered");
        long failed = events.countByStatus("failed");
        long dead = events.countByStatus("dead_lettered");
        long pendingOutbox = outbox.countByStatus("PENDING");
        Map<String, Long> main = queueCounts(queued, processing, delivered, failed + dead, pendingOutbox);
        Map<String, Long> dlq = queueCounts(dead, 0, 0, 0, 0);
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (String status : new String[]{"received", "queued", "processing", "delivered", "failed", "dead_lettered"}) {
            long count = events.countByStatus(status);
            if (count > 0) byStatus.put(status, count);
        }
        return Map.of(
                "generatedAt", clock.instant(),
                "queues", Map.of(properties.getQueue().getMainStream(), main,
                        properties.getQueue().getDeadLetterStream(), dlq),
                "events", Map.of("total", events.count(), "byStatus", byStatus),
                "outbox", Map.of("pending", pendingOutbox));
    }

    private Map<String, Long> queueCounts(long waiting, long active, long completed, long failed, long delayed) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("waiting", waiting);
        result.put("active", active);
        result.put("completed", completed);
        result.put("failed", failed);
        result.put("delayed", delayed);
        result.put("paused", 0L);
        return result;
    }

    private Map<String, Object> checkDatabase() {
        try { jdbc.queryForObject("SELECT 1", Integer.class); return Map.of("status", "up"); }
        catch (RuntimeException error) { return Map.of("status", "down", "message", safeMessage(error)); }
    }

    private Map<String, Object> checkRedis() {
        try {
            try (var connection = redis.getConnection()) { connection.ping(); }
            return Map.of("status", "up");
        } catch (RuntimeException error) { return Map.of("status", "down", "message", safeMessage(error)); }
    }

    private String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? "Unknown failure" : error.getMessage();
    }
}
