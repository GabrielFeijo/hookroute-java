package com.gabrielfeijo.hookroute.api.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WebhookSignatureFilterTest {
    @Test
    void verifiesRawBodyAndTimestamp() throws Exception {
        var properties = new HookRouteProperties();
        properties.getSecurity().setWebhookSecret("secret");
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
        var filter = new WebhookSignatureFilter(properties, clock, new ObjectMapper());
        byte[] body = "{\"eventType\":\"order.created\",\"data\":{}}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(clock.millis());

        assertTrue(filter.verify(body, timestamp, sign(body, timestamp, "secret")));
        assertFalse(filter.verify("{}".getBytes(StandardCharsets.UTF_8), timestamp, sign(body, timestamp, "secret")));
        assertFalse(filter.verify(body, String.valueOf(clock.millis() - 300_001), sign(body, timestamp, "secret")));
    }

    private String sign(byte[] body, String timestamp, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
