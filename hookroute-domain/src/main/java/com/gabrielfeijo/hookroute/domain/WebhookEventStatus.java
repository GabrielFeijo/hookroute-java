package com.gabrielfeijo.hookroute.domain;

public enum WebhookEventStatus {
    RECEIVED("received"),
    QUEUED("queued"),
    PROCESSING("processing"),
    DELIVERED("delivered"),
    FAILED("failed"),
    DEAD_LETTERED("dead_lettered");

    private final String value;

    WebhookEventStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static WebhookEventStatus fromValue(String value) {
        for (var status : values()) {
            if (status.value.equals(value)) return status;
        }
        throw new IllegalArgumentException("Unknown webhook status: " + value);
    }
}
