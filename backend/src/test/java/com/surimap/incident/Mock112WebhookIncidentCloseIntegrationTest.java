package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.external.ExternalIncidentAdapter;
import com.surimap.incident.event.IncidentClosedEvent;
import com.surimap.incident.event.IncidentEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(Mock112WebhookIncidentCloseIntegrationTest.FixedClockConfig.class)
@Sql(
    statements = {
      "CREATE TABLE IF NOT EXISTS \"incident\" (id VARCHAR(36) PRIMARY KEY, source_incident_id UUID NOT NULL UNIQUE, title VARCHAR(200) NOT NULL, status VARCHAR(32) NOT NULL, opened_at TIMESTAMP WITH TIME ZONE, closed_at TIMESTAMP WITH TIME ZONE, closed_by_account_id VARCHAR(36), version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS missing_person (incident_id VARCHAR(36) PRIMARY KEY, display_name VARCHAR(120) NOT NULL, photo_object_key CLOB, appearance_text CLOB, last_seen_location_text VARCHAR(255), last_seen_at TIMESTAMP WITH TIME ZONE, imported_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS mock112_webhook_event (event_id VARCHAR(120) PRIMARY KEY, event_type VARCHAR(80) NOT NULL, source_incident_id UUID NOT NULL, request_body_hash VARCHAR(128) NOT NULL, webhook_status VARCHAR(32) NOT NULL, incident_id UUID, incident_status VARCHAR(32), incident_version BIGINT, response_body_json CLOB, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, CONSTRAINT chk_mock112_webhook_event_type CHECK (event_type IN ('INCIDENT_READY', 'INCIDENT_ASSIGNMENT_CHANGED', 'INCIDENT_CLOSED')), CONSTRAINT chk_mock112_webhook_status CHECK (webhook_status IN ('RESERVED', 'COMPLETED')))",
      "DELETE FROM mock112_webhook_event",
      "DELETE FROM missing_person",
      "DELETE FROM \"incident\"",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '00000000-0000-0000-0000-000000000001', '광주 무등산 실종 신고', 'OPEN', '2026-04-28T09:00:00+09:00', NULL, NULL, 1, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO missing_person (incident_id, display_name, photo_object_key, appearance_text, last_seen_location_text, last_seen_at, imported_at) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '가상 실종자 001', 'mock-112/missing-person/mock-112-incident-001.jpg', '남색 점퍼, 회색 등산화', '무등산 서측 탐방로 입구', '2026-04-28T08:30:00+09:00', '2026-04-28T09:00:00+09:00')"
    })
@DisplayName("mock-112 INCIDENT_CLOSED internal webhook")
class Mock112WebhookIncidentCloseIntegrationTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final String SOURCE_INCIDENT_ID = "00000000-0000-0000-0000-000000000001";
  private static final String EVENT_ID = "mock112:INCIDENT_CLOSED:" + SOURCE_INCIDENT_ID;
  private static final Instant CLOSED_AT = Instant.parse("2026-05-20T05:00:00Z");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private ExternalIncidentAdapter externalIncidentAdapter;

  @MockitoBean private IncidentEventPublisher incidentEventPublisher;

  @BeforeEach
  void resetPublisher() {
    reset(incidentEventPublisher);
  }

  @Test
  @DisplayName("INCIDENT_CLOSED webhook은 import된 OPEN 사건을 terminal CLOSED로 닫고 PII를 삭제한다")
  void incidentClosedWebhookClosesIncidentAndDeletesMissingPerson() throws Exception {
    mockMvc
        .perform(
            post("/api/internal/mock-112/events")
                .header("X-Client-Channel", "INTERNAL")
                .contentType(MediaType.APPLICATION_JSON)
                .content(closeWebhookBody()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.eventId", is(EVENT_ID)))
        .andExpect(jsonPath("$.eventType", is("INCIDENT_CLOSED")))
        .andExpect(jsonPath("$.sourceIncidentId", is(SOURCE_INCIDENT_ID)))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.status", is("CLOSED")))
        .andExpect(jsonPath("$.version", is(2)));

    assertThat(singleString("SELECT status FROM \"incident\" WHERE id = ?")).isEqualTo("CLOSED");
    assertThat(singleLong("SELECT version FROM \"incident\" WHERE id = ?")).isEqualTo(2L);
    assertThat(singleString("SELECT closed_by_account_id FROM \"incident\" WHERE id = ?")).isNull();
    assertThat(count("missing_person", "incident_id = ?", INCIDENT_ID.toString())).isZero();
    assertThat(
            singleString(
                "SELECT webhook_status FROM mock112_webhook_event WHERE event_id = ?", EVENT_ID))
        .isEqualTo("COMPLETED");
    assertThat(
            singleString(
                "SELECT incident_status FROM mock112_webhook_event WHERE event_id = ?", EVENT_ID))
        .isEqualTo("CLOSED");

    mockMvc
        .perform(
            post("/api/internal/mock-112/events")
                .header("X-Client-Channel", "INTERNAL")
                .contentType(MediaType.APPLICATION_JSON)
                .content(closeWebhookBody()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status", is("CLOSED")))
        .andExpect(jsonPath("$.version", is(2)));

    assertThat(count("mock112_webhook_event", "event_id = ?", EVENT_ID)).isEqualTo(1);
    verify(incidentEventPublisher, times(1))
        .publishIncidentClosed(
            argThat(Mock112WebhookIncidentCloseIntegrationTest::closedEventMatches));
  }

  private static boolean closedEventMatches(IncidentClosedEvent event) {
    return event.id().equals(INCIDENT_ID)
        && event.status().equals("CLOSED")
        && event.version() == 2L
        && event.closedAt().equals(CLOSED_AT)
        && event.writeDisabledReason().equals("incident_closed");
  }

  private static String closeWebhookBody() {
    return """
        {
          "eventId": "mock112:INCIDENT_CLOSED:00000000-0000-0000-0000-000000000001",
          "eventType": "INCIDENT_CLOSED",
          "sourceIncidentId": "00000000-0000-0000-0000-000000000001",
          "occurredAt": "2026-05-20T14:00:00+09:00",
          "closeReason": "FOUND_SAFE"
        }
        """;
  }

  private String singleString(String sql) {
    return jdbc.queryForObject(sql, String.class, INCIDENT_ID.toString());
  }

  private String singleString(String sql, String value) {
    return jdbc.queryForObject(sql, String.class, value);
  }

  private long singleLong(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class, INCIDENT_ID.toString());
    return value == null ? 0L : value;
  }

  private long count(String table, String where, String value) {
    Long result =
        jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class, value);
    return result == null ? 0L : result;
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(CLOSED_AT, ZoneOffset.UTC);
    }
  }
}
