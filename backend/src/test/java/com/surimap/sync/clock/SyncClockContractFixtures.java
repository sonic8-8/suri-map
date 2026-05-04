package com.surimap.sync.clock;

final class SyncClockContractFixtures {

    static final String INCIDENT_ID = "inc-precinct-first-001";
    static final String DEVICE_ID = "dev-precinct-car-01";
    static final String CLIENT_TS = "2026-04-28T09:10:00+09:00";
    static final String SKEWED_CLIENT_TS = "2026-04-28T09:10:35+09:00";
    static final int MAX_ALLOWED_SKEW_MS = 30_000;

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
