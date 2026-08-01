package com.gabrielfeijo.hookroute.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gabrielfeijo.hookroute.infrastructure.config.HookRouteProperties;
import com.gabrielfeijo.hookroute.infrastructure.service.DeliveryLifecycleService;
import com.gabrielfeijo.hookroute.infrastructure.service.EgressService;
import com.gabrielfeijo.hookroute.infrastructure.service.EgressService.EgressException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class WebhookStreamConsumerTest {
    @Test
    void leavesMessagePendingWhenLifecycleStorageIsUnavailable() {
        var redis = mock(StringRedisTemplate.class);
        var lifecycle = mock(DeliveryLifecycleService.class);
        var egress = mock(EgressService.class);
        var consumer = new WebhookStreamConsumer(redis, new ObjectMapper(), lifecycle, egress,
                new HookRouteProperties());
        var record = record();
        when(lifecycle.begin(any())).thenThrow(new DataAccessResourceFailureException("database unavailable"));

        consumer.process(record);

        verifyNoInteractions(redis, egress);
    }

    @Test
    void leavesMessagePendingWhenDeliveredStateCannotBePersisted() {
        var redis = mock(StringRedisTemplate.class);
        var lifecycle = mock(DeliveryLifecycleService.class);
        var egress = mock(EgressService.class);
        var consumer = new WebhookStreamConsumer(redis, new ObjectMapper(), lifecycle, egress,
                new HookRouteProperties());
        var record = record();
        var work = new DeliveryLifecycleService.WorkItem("order.created",
                new ObjectMapper().readTree("{\"data\":{}}"), "correlation");
        when(lifecycle.begin(any())).thenReturn(new DeliveryLifecycleService.Acquisition(work, false));
        when(egress.destination("order.created")).thenReturn("https://example.test/order.created");
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(lifecycle).delivered(any(), any());

        consumer.process(record);

        verify(egress).dispatch(any(), any(), any(), any());
        verifyNoInteractions(redis);
    }

    @Test
    void keepsBusyDuplicatePendingWithoutDispatching() {
        var redis = mock(StringRedisTemplate.class);
        var lifecycle = mock(DeliveryLifecycleService.class);
        var egress = mock(EgressService.class);
        var consumer = new WebhookStreamConsumer(redis, new ObjectMapper(), lifecycle, egress,
                new HookRouteProperties());
        when(lifecycle.begin(any())).thenReturn(new DeliveryLifecycleService.Acquisition(null, true));

        consumer.process(record());

        verifyNoInteractions(redis, egress);
    }

    @Test
    void leavesMessagePendingWhenFailedStateCannotBePersisted() {
        var redis = mock(StringRedisTemplate.class);
        var lifecycle = mock(DeliveryLifecycleService.class);
        var egress = mock(EgressService.class);
        var consumer = new WebhookStreamConsumer(redis, new ObjectMapper(), lifecycle, egress,
                new HookRouteProperties());
        var work = new DeliveryLifecycleService.WorkItem("order.created",
                new ObjectMapper().readTree("{\"data\":{}}"), "correlation");
        when(lifecycle.begin(any())).thenReturn(new DeliveryLifecycleService.Acquisition(work, false));
        when(egress.destination("order.created")).thenReturn("https://example.test/order.created");
        when(egress.dispatch(any(), any(), any(), any())).thenThrow(new EgressException("temporary failure"));
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(lifecycle).failed(any(), any(), any());

        consumer.process(record());

        verify(lifecycle).failed(any(), any(), any());
        verifyNoInteractions(redis);
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, Object, Object> record() {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("1-0"));
        when(record.getValue()).thenReturn(Map.of("body", """
                {"jobId":"00000000-0000-0000-0000-000000000001",
                 "eventId":"00000000-0000-0000-0000-000000000002",
                 "attempt":1,"correlationId":"correlation"}
                """));
        return record;
    }
}
