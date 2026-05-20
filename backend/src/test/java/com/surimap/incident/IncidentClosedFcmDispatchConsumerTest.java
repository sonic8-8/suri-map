package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.incident.service.IncidentClosedFcmDispatchConsumer;
import com.surimap.marker.notification.adapter.MockFcmDispatcher;
import com.surimap.policephone.FcmTokenStatus;
import com.surimap.policephone.query.FcmTokenQuery;
import com.surimap.policephone.query.FcmTokenRow;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IncidentClosedFcmDispatchConsumerTest {

  private static final UUID EVENT_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000012");
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110002");
  private static final UUID POLICE_PHONE_ID = UUID.fromString("22222222-2222-2222-2222-222222220001");
  private static final String CLOSED_AT = "2026-05-20T06:16:39.613400Z";

  @Test
  @DisplayName("INCIDENT_CLOSED 이벤트는 배정 계정의 FCM 토큰으로 종료 data message를 발송한다")
  void incidentClosedDispatchesFcmToActiveAssignmentTokens() {
    IncidentMapper incidentMapper = mock(IncidentMapper.class);
    FcmTokenQuery fcmTokenQuery = mock(FcmTokenQuery.class);
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    IncidentClosedFcmDispatchConsumer consumer =
        new IncidentClosedFcmDispatchConsumer(incidentMapper, fcmTokenQuery, dispatcher);

    when(incidentMapper.findActiveAssignmentAccountIds(INCIDENT_ID))
        .thenReturn(List.of(ACCOUNT_ID.toString()));
    when(fcmTokenQuery.activeByAccounts(List.of(ACCOUNT_ID)))
        .thenReturn(
            List.of(
                new FcmTokenRow(
                    UUID.fromString("33333333-3333-3333-3333-333333330001"),
                    POLICE_PHONE_ID,
                    "app-instance-001",
                    "cipher:fcm-token-001",
                    "hash-001",
                    FcmTokenStatus.ACTIVE,
                    1L)));

    consumer.consume(incidentClosedEvent());

    MockFcmDispatcher.CapturedDispatch captured =
        dispatcher.findByEventId(EVENT_ID.toString()).orElseThrow();
    assertThat(captured.recipients()).containsExactly("fcm-token-001");
    assertThat(captured.getPayloadField("type")).isEqualTo("INCIDENT_CLOSED");
    assertThat(captured.getPayloadField("incidentId")).isEqualTo(INCIDENT_ID.toString());
    assertThat(captured.getPayloadField("status")).isEqualTo("CLOSED");
    assertThat(captured.getPayloadField("version")).isEqualTo("3");
    assertThat(captured.getPayloadField("closedAt")).isEqualTo(CLOSED_AT);
    assertThat(captured.getPayloadField("writeDisabledReason")).isEqualTo("incident_closed");
    assertThat(captured.recipientAccountIds()).containsExactly(ACCOUNT_ID.toString());
    assertThat(captured.recipientPolicePhoneIds()).containsExactly(POLICE_PHONE_ID.toString());
  }

  private PublishRequest incidentClosedEvent() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("status", "CLOSED");
    payload.put("version", 3);
    payload.put("closedAt", CLOSED_AT);
    payload.put("writeDisabledReason", "incident_closed");
    return new PublishRequest(
        EVENT_ID,
        INCIDENT_ID,
        "INCIDENT_CLOSED",
        1,
        "incident",
        INCIDENT_ID,
        Instant.parse(CLOSED_AT),
        payload);
  }
}
