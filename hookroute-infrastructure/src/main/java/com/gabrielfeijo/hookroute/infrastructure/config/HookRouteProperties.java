package com.gabrielfeijo.hookroute.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hookroute")
public class HookRouteProperties {
    private final Queue queue = new Queue();
    private final Egress egress = new Egress();
    private final Security security = new Security();

    public Queue getQueue() { return queue; }
    public Egress getEgress() { return egress; }
    public Security getSecurity() { return security; }

    public static class Queue {
        private String mainStream = "webhook-events";
        private String deadLetterStream = "webhook-events-dlq";
        private String consumerGroup = "hookroute-workers";
        private String consumerName = "worker";
        private int batchSize = 10;
        private long pollDelayMs = 250;
        private long visibilityTimeoutMs = 30000;

        public String getMainStream() { return mainStream; }
        public void setMainStream(String value) { mainStream = value; }
        public String getDeadLetterStream() { return deadLetterStream; }
        public void setDeadLetterStream(String value) { deadLetterStream = value; }
        public String getConsumerGroup() { return consumerGroup; }
        public void setConsumerGroup(String value) { consumerGroup = value; }
        public String getConsumerName() { return consumerName; }
        public void setConsumerName(String value) { consumerName = value; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int value) { batchSize = value; }
        public long getPollDelayMs() { return pollDelayMs; }
        public void setPollDelayMs(long value) { pollDelayMs = value; }
        public long getVisibilityTimeoutMs() { return visibilityTimeoutMs; }
        public void setVisibilityTimeoutMs(long value) { visibilityTimeoutMs = value; }
    }

    public static class Egress {
        private String mode = "simulated";
        private String destinationBaseUrl = "https://example.com/webhooks";
        private long timeoutMs = 3000;
        private long simulatedLatencyMs = 50;
        private int circuitFailureThreshold = 3;
        private long circuitOpenMs = 30000;
        private boolean allowPayloadSimulation = true;

        public String getMode() { return mode; }
        public void setMode(String value) { mode = value; }
        public String getDestinationBaseUrl() { return destinationBaseUrl; }
        public void setDestinationBaseUrl(String value) { destinationBaseUrl = value; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long value) { timeoutMs = value; }
        public long getSimulatedLatencyMs() { return simulatedLatencyMs; }
        public void setSimulatedLatencyMs(long value) { simulatedLatencyMs = value; }
        public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
        public void setCircuitFailureThreshold(int value) { circuitFailureThreshold = value; }
        public long getCircuitOpenMs() { return circuitOpenMs; }
        public void setCircuitOpenMs(long value) { circuitOpenMs = value; }
        public boolean isAllowPayloadSimulation() { return allowPayloadSimulation; }
        public void setAllowPayloadSimulation(boolean value) { allowPayloadSimulation = value; }
    }

    public static class Security {
        private String webhookSecret = "";
        private String adminApiKey = "";
        private long signatureToleranceMs = 300000;

        public String getWebhookSecret() { return webhookSecret; }
        public void setWebhookSecret(String value) { webhookSecret = value; }
        public String getAdminApiKey() { return adminApiKey; }
        public void setAdminApiKey(String value) { adminApiKey = value; }
        public long getSignatureToleranceMs() { return signatureToleranceMs; }
        public void setSignatureToleranceMs(long value) { signatureToleranceMs = value; }
    }
}
