package com.surimap.sync.clock;

import java.util.List;

final class SyncClockContractFixtures {

    static final String PATH = "/api/sync/clock";
    static final String INCIDENT_ID = "inc-precinct-first-001";
    static final String DEVICE_ID = "dev-precinct-car-01";
    static final String CLIENT_TS = "2026-04-28T09:10:00+09:00";
    static final String SKEWED_CLIENT_TS = "2026-04-28T09:10:35+09:00";
    static final int MAX_ALLOWED_SKEW_MS = 30_000;
    static final List<String> REQUIRED_HEADERS = List.of("Authorization", "X-Device-Id");
    static final List<String> RESPONSE_FIELDS = List.of(
            "clientTs",
            "serverTs",
            "clockOffsetMs",
            "clockSyncedAt",
            "maxAllowedSkewMs"
    );
    static final List<String> GUARD_ERROR_CODES = List.of(
            "channel_not_allowed",
            "device_required",
            "device_not_registered",
            "device_not_assigned"
    );
    static final String CLOCK_SKEW_ERROR_CODE = "clock_skew_exceeded";

    static final String VALID_REQUEST = """
            {
              "incidentId": "%s",
              "clientTs": "%s"
            }
            """.formatted(INCIDENT_ID, CLIENT_TS);

    static final String SKEWED_REQUEST = """
            {
              "incidentId": "%s",
              "clientTs": "%s"
            }
            """.formatted(INCIDENT_ID, SKEWED_CLIENT_TS);

    private SyncClockContractFixtures() {
    }
}
