package com.mock112.webhook;

public record WebhookDeliveryResult(
        String status,
        int eventCount,
        int sentCount,
        int pendingCount,
        int failedCount,
        int skippedCount) {

    public static WebhookDeliveryResult skipped(int eventCount) {
        return new WebhookDeliveryResult("DISABLED", eventCount, 0, 0, 0, eventCount);
    }

    public static WebhookDeliveryResult none() {
        return new WebhookDeliveryResult("NO_EVENTS", 0, 0, 0, 0, 0);
    }

    public static WebhookDeliveryResult sent() {
        return new WebhookDeliveryResult("SENT", 1, 1, 0, 0, 0);
    }

    public static WebhookDeliveryResult pending() {
        return new WebhookDeliveryResult("PENDING", 1, 0, 1, 0, 0);
    }

    public static WebhookDeliveryResult failed() {
        return new WebhookDeliveryResult("FAILED", 1, 0, 0, 1, 0);
    }

    public WebhookDeliveryResult plus(WebhookDeliveryResult other) {
        int events = eventCount + other.eventCount;
        int sent = sentCount + other.sentCount;
        int pending = pendingCount + other.pendingCount;
        int failed = failedCount + other.failedCount;
        int skipped = skippedCount + other.skippedCount;
        return new WebhookDeliveryResult(statusOf(sent, pending, failed, skipped, events), events, sent, pending, failed, skipped);
    }

    private static String statusOf(int sent, int pending, int failed, int skipped, int events) {
        if (events == 0) {
            return "NO_EVENTS";
        }
        if (failed > 0) {
            return "FAILED";
        }
        if (pending > 0) {
            return "PENDING";
        }
        if (skipped == events) {
            return "DISABLED";
        }
        if (sent == events) {
            return "SENT";
        }
        return "PARTIAL";
    }
}
