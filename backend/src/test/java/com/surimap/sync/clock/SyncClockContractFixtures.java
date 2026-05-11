package com.surimap.sync.clock;

final class SyncClockContractFixtures {

  static final String INCIDENT_ID = "b5fdbad6-57ce-4d64-a6f2-82b1d3e16699";
  static final String POLICE_PHONE_HEADER = "X-PolicePhone-Id";
  static final String ASSIGNED_POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101";
  static final String UNREGISTERED_POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000201";
  static final String UNASSIGNED_POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000301";
  static final String CLIENT_TS = "2026-04-28T09:00:40+09:00";
  static final String SKEWED_CLIENT_TS = "2026-04-28T09:01:20+09:00";
  static final String SERVER_TS = "2026-04-28T09:00:41+09:00";
  static final long CLOCK_OFFSET_MS = 1_000L;
  static final long MAX_ALLOWED_SKEW_MS = 30_000L;

  private SyncClockContractFixtures() {}

  static String requestBody() {
    return """
                {
                  "incidentId": "%s",
                  "clientTs": "%s"
                }
                """
        .formatted(INCIDENT_ID, CLIENT_TS);
  }

  static String skewedRequestBody() {
    return """
                {
                  "incidentId": "%s",
                  "clientTs": "%s"
                }
                """
        .formatted(INCIDENT_ID, SKEWED_CLIENT_TS);
  }
}
