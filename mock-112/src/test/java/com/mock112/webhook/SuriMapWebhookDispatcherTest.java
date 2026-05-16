package com.mock112.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("mock-112 Suri-Map webhook dispatcher")
class SuriMapWebhookDispatcherTest {

    @Test
    @DisplayName("assignment changed eventId is deterministic and bounded for backend idempotency key")
    void assignmentChangedEventIdIsDeterministicAndBounded() {
        String sourceIncidentId = "00000000-0000-0000-0000-000000000001";
        String longEventKey =
                "acct-support-team:external-assignment-key-that-can-grow-past-the-backend-event-id-column";

        String eventId = SuriMapWebhookDispatcher.assignmentChangedEventId(sourceIncidentId, longEventKey);

        assertThat(eventId).isEqualTo(SuriMapWebhookDispatcher.assignmentChangedEventId(sourceIncidentId, longEventKey));
        assertThat(eventId).startsWith("mock112:ASSIGNMENT_CHANGED:");
        assertThat(eventId).hasSizeLessThanOrEqualTo(120);
    }
}
