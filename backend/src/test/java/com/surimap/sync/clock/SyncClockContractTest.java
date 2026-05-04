package com.surimap.sync.clock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
class SyncClockContractTest {

    @Test
    @DisplayName("POST /api/sync/clock 계약 fixture는 요청/응답 필드와 헤더를 고정해야 한다")
    void syncClockContractFixtureFreezesRequestAndResponseShape() {
        assertThat(SyncClockContractFixtures.PATH).isEqualTo("/api/sync/clock");
        assertThat(SyncClockContractFixtures.REQUIRED_HEADERS)
                .containsExactly("Authorization", "X-Device-Id");
        assertThat(SyncClockContractFixtures.VALID_REQUEST)
                .contains("\"incidentId\": \"" + SyncClockContractFixtures.INCIDENT_ID + "\"")
                .contains("\"clientTs\": \"" + SyncClockContractFixtures.CLIENT_TS + "\"");
        assertThat(SyncClockContractFixtures.RESPONSE_FIELDS)
                .containsExactly(
                        "clientTs",
                        "serverTs",
                        "clockOffsetMs",
                        "clockSyncedAt",
                        "maxAllowedSkewMs"
                );
        assertThat(SyncClockContractFixtures.MAX_ALLOWED_SKEW_MS).isEqualTo(30_000);
    }

    @Test
    @DisplayName("POST /api/sync/clock 계약 fixture는 guard 오류와 clock skew 오류를 고정해야 한다")
    void syncClockContractFixtureFreezesGuardAndSkewFailures() {
        assertThat(SyncClockContractFixtures.SKEWED_REQUEST)
                .contains("\"incidentId\": \"" + SyncClockContractFixtures.INCIDENT_ID + "\"")
                .contains("\"clientTs\": \"" + SyncClockContractFixtures.SKEWED_CLIENT_TS + "\"");
        assertThat(SyncClockContractFixtures.GUARD_ERROR_CODES)
                .containsExactly(
                        "channel_not_allowed",
                        "device_required",
                        "device_not_registered",
                        "device_not_assigned"
                );
        assertThat(SyncClockContractFixtures.CLOCK_SKEW_ERROR_CODE)
                .isEqualTo("clock_skew_exceeded");
    }
}
