package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(addFilters = false)
@DisplayName("APP 쓰기 API의 업무폰 ID 헤더 확인")
class AppWritePolicePhoneHeaderApiTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000005101");
  private static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000005101");
  private static final UUID OVERALL_AREA_ID =
      UUID.fromString("72000000-0000-0000-0000-000000005100");
  private static final UUID TEAM_AREA_ID = UUID.fromString("72000000-0000-0000-0000-000000005101");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000005101");
  private static final UUID COMMANDER_ID = UUID.fromString("63000000-0000-0000-0000-000000005101");
  private static final UUID INCIDENT_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-0000-0000-000000005101");
  // 이 업무폰 ID는 police_phone 테이블에 없다.
  private static final UUID UNKNOWN_POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000005101");
  private static final Instant BASE_TS = Instant.parse("2026-11-19T00:00:00Z");

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void seedIncidentWithoutPolicePhoneRows() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          event_dispatch_job, idempotency_record, fcm_token, photo, marker,
          handover_memo, search_path_lifecycle_event, search_path_segment, search_path,
          duty_shift, search_area_assignment, search_area, operational_period,
          incident_assignment, police_phone, account, incident
        CASCADE
        """);
    Timestamp base = Timestamp.from(BASE_TS);
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?, gen_random_uuid(), 'Police phone header incident', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID,
        base,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES
          (?, 'acct-phone-header-app', '{noop}fixture', '현장 계정', 'TEAM', 'MISSING_TEAM',
           'ACTIVE', ?, ?),
          (?, 'acct-phone-header-command', '{noop}fixture', '지휘 계정', 'COMMAND',
           'POLICE_SUBSTATION', 'ACTIVE', ?, ?)
        """,
        ACCOUNT_ID,
        base,
        base,
        COMMANDER_ID,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at
        )
        VALUES (?, ?, ?, 'MEMBER', ?, ?, ?)
        """,
        INCIDENT_ASSIGNMENT_ID,
        INCIDENT_ID,
        ACCOUNT_ID,
        base,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_by_account_id,
          started_at, version, created_at, updated_at
        )
        VALUES (?, ?, 1, 'ACTIVE', 'INITIAL_IMPORT', ?, ?, 1, ?, ?)
        """,
        OP_ID,
        INCIDENT_ID,
        COMMANDER_ID,
        base,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO search_area (
          id, operational_period_id, parent_search_area_id, name, area_level, geometry, status,
          version, created_by_account_id, created_at, updated_at
        )
        VALUES
          (?, ?, NULL, '전체 구역', 'OVERALL',
           ST_GeomFromText('POLYGON((126.904000 35.158000,126.923000 35.158000,126.923000 35.173000,126.904000 35.173000,126.904000 35.158000))', 4326),
           'ACTIVE', 1, ?, ?, ?),
          (?, ?, NULL, 'A팀 담당 구역', 'TEAM',
           ST_GeomFromText('POLYGON((126.910000 35.160000,126.918000 35.160000,126.918000 35.166000,126.910000 35.166000,126.910000 35.160000))', 4326),
           'ACTIVE', 1, ?, ?, ?)
        """,
        OVERALL_AREA_ID,
        OP_ID,
        COMMANDER_ID,
        base,
        base,
        TEAM_AREA_ID,
        OP_ID,
        COMMANDER_ID,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO search_area_assignment (
          id, search_area_id, assigned_account_id, assigned_by_account_id, assigned_at, status,
          created_at, updated_at
        )
        VALUES (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        TEAM_AREA_ID,
        ACCOUNT_ID,
        COMMANDER_ID,
        base,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
          id, operational_period_id, incident_assignment_id, status,
          started_by_account_id, started_at, version, created_at, updated_at
        )
        VALUES (gen_random_uuid(), ?, ?, 'ACTIVE', ?, ?, 1, ?, ?)
        """,
        OP_ID,
        INCIDENT_ASSIGNMENT_ID,
        ACCOUNT_ID,
        base,
        base,
        base);
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로도 마커를 만들 수 있다")
  void create_marker_with_unknown_police_phone_succeeds() throws Exception {
    perform(markerCreate(), UNKNOWN_POLICE_PHONE_ID.toString()).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로도 사진 업로드 URL을 받을 수 있다")
  void create_photo_upload_url_with_unknown_police_phone_succeeds() throws Exception {
    perform(photoUploadUrl(), UNKNOWN_POLICE_PHONE_ID.toString()).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로도 수색 경로를 시작할 수 있다")
  void start_search_path_with_unknown_police_phone_succeeds() throws Exception {
    perform(searchPathStart(), UNKNOWN_POLICE_PHONE_ID.toString())
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로도 근무를 시작할 수 있다")
  void start_duty_shift_with_unknown_police_phone_succeeds() throws Exception {
    // given: 시작할 근무와 겹치지 않도록 seed의 근무를 지운다.
    jdbcTemplate.update("DELETE FROM duty_shift");

    perform(dutyShiftStart(), UNKNOWN_POLICE_PHONE_ID.toString()).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로도 인수인계 메모를 남길 수 있다")
  void create_handover_memo_with_unknown_police_phone_succeeds() throws Exception {
    perform(handoverMemoCreate(), UNKNOWN_POLICE_PHONE_ID.toString())
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로도 수색 구역 경계 알림이 저장되고 업무폰 ID가 남는다")
  void create_boundary_alert_with_unknown_police_phone_stores_police_phone_id() throws Exception {
    perform(boundaryAlertCreate(), UNKNOWN_POLICE_PHONE_ID.toString())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.policePhoneId", is(UNKNOWN_POLICE_PHONE_ID.toString())));

    List<String> storedPolicePhoneIds =
        jdbcTemplate.queryForList(
            "SELECT police_phone_id::text FROM search_area_boundary_alert", String.class);
    assertThat(storedPolicePhoneIds).containsExactly(UNKNOWN_POLICE_PHONE_ID.toString());
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로도 FCM 토큰을 등록할 수 있다")
  void register_fcm_token_with_unknown_police_phone_succeeds() throws Exception {
    perform(fcmTokenRegister(), UNKNOWN_POLICE_PHONE_ID.toString()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로도 시각 동기화를 할 수 있다")
  void sync_clock_with_unknown_police_phone_succeeds() throws Exception {
    perform(syncClock(), UNKNOWN_POLICE_PHONE_ID.toString()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로도 미전송 기록 재전송을 요청할 수 있다")
  void requeue_outbox_with_unknown_police_phone_succeeds() throws Exception {
    perform(outboxRequeue(), UNKNOWN_POLICE_PHONE_ID.toString()).andExpect(status().isAccepted());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("appWriteRequests")
  @DisplayName("업무폰 ID 헤더가 없으면 police_phone_required로 거절된다")
  void app_write_without_police_phone_header_is_rejected(
      String name, MockHttpServletRequestBuilder request) throws Exception {
    perform(request, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));
  }

  @ParameterizedTest(name = "{0}, {2}")
  @MethodSource("appWriteRequestsWithMalformedPolicePhoneIds")
  @DisplayName("업무폰 ID 헤더가 36자 UUID 형식이 아니면 police_phone_required로 거절된다")
  void app_write_with_non_uuid_police_phone_header_is_rejected(
      String name, MockHttpServletRequestBuilder request, String policePhoneId) throws Exception {
    perform(request, policePhoneId)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));
  }

  // 구획 자릿수가 모자란 1-1-1-1-1도 UUID.fromString에서는 파싱되므로 이 값을 따로 넣었다.
  static Stream<Arguments> appWriteRequestsWithMalformedPolicePhoneIds() {
    return Stream.of("phone-not-uuid", "1-1-1-1-1")
        .flatMap(
            policePhoneId ->
                appWriteRequests()
                    .map(arguments -> Arguments.of(arguments.get()[0], arguments.get()[1], policePhoneId)));
  }

  static Stream<Arguments> appWriteRequests() {
    return Stream.of(
        Arguments.of("마커", markerCreate()),
        Arguments.of("사진", photoUploadUrl()),
        Arguments.of("수색 경로", searchPathStart()),
        Arguments.of("근무 시작", dutyShiftStart()),
        Arguments.of("인수인계 메모", handoverMemoCreate()),
        Arguments.of("수색 구역 경계 알림", boundaryAlertCreate()),
        Arguments.of("FCM 토큰", fcmTokenRegister()),
        Arguments.of("FCM 토큰 연결 해제", fcmTokenRevoke()),
        Arguments.of("시각 동기화", syncClock()),
        Arguments.of("미전송 기록 재전송", outboxRequeue()));
  }

  // 운영에서는 인증 필터가 X-PolicePhone-Id 헤더 값을 인증 정보에 담는다. 테스트도 같은 값을 넣는다.
  private ResultActions perform(MockHttpServletRequestBuilder request, String policePhoneId)
      throws Exception {
    request
        .header("X-Client-Channel", "APP")
        .header("Authorization", "Bearer app-token")
        .header("Idempotency-Key", "idem-" + UUID.randomUUID());
    TestSecurityContextHolder.setAuthentication(appAuthentication(policePhoneId));
    if (policePhoneId != null) {
      request.header("X-PolicePhone-Id", policePhoneId);
    }
    return mockMvc.perform(request);
  }

  private static SuriMapAuthentication appAuthentication(String policePhoneId) {
    return new SuriMapAuthentication(
        ACCOUNT_ID.toString(),
        AccountType.TEAM,
        OrganizationType.MISSING_TEAM,
        Channel.APP,
        policePhoneId,
        List.of(new SimpleGrantedAuthority(Role.MEMBER.name())));
  }

  private static MockHttpServletRequestBuilder markerCreate() {
    return json(
        post("/api/markers"),
        """
        {
          "incidentId": "%s",
          "opId": "%s",
          "type": "CLUE",
          "location": { "type": "Point", "coordinates": [126.913400, 35.163100] },
          "memo": "업무폰 기록이 없는 마커",
          "clientTs": "2026-11-19T09:05:00+09:00",
          "clockOffsetMs": 0
        }
        """
            .formatted(INCIDENT_ID, OP_ID));
  }

  private static MockHttpServletRequestBuilder photoUploadUrl() {
    return json(
        post("/api/markers/photos/upload-url"),
        """
        {
          "markerId": "%s",
          "incidentId": "%s",
          "opId": "%s",
          "contentType": "image/jpeg",
          "sizeBytes": 1048576
        }
        """
            .formatted(UUID.randomUUID(), INCIDENT_ID, OP_ID));
  }

  private static MockHttpServletRequestBuilder searchPathStart() {
    return json(
        post("/api/search-paths"),
        """
        {
          "searchPathId": "%s",
          "incidentId": "%s",
          "opId": "%s",
          "clientTs": "2026-11-19T00:10:00Z",
          "clockOffsetMs": 0
        }
        """
            .formatted(UUID.randomUUID(), INCIDENT_ID, OP_ID));
  }

  private static MockHttpServletRequestBuilder dutyShiftStart() {
    return json(
        post("/api/duty-shifts"),
        """
        {
          "incidentId": "%s",
          "opId": "%s",
          "clientTs": "2026-11-19T09:00:00+09:00"
        }
        """
            .formatted(INCIDENT_ID, OP_ID));
  }

  private static MockHttpServletRequestBuilder handoverMemoCreate() {
    return json(
        post("/api/handover-memos"),
        """
        {
          "incidentId": "%s",
          "opId": "%s",
          "memoTargetType": "OPERATIONAL_PERIOD",
          "memoTargetId": "%s",
          "content": "업무폰 기록이 없는 인수인계 메모",
          "clientTs": "2026-11-19T09:20:00+09:00"
        }
        """
            .formatted(INCIDENT_ID, OP_ID, OP_ID));
  }

  private static MockHttpServletRequestBuilder boundaryAlertCreate() {
    return json(
        post("/api/search-area-boundary-alerts"),
        """
        {
          "incidentId": "%s",
          "opId": "%s",
          "searchAreaId": "%s",
          "alertType": "OUTSIDE_ASSIGNED_AREA",
          "location": { "type": "Point", "coordinates": [126.920000, 35.168000] },
          "clientTs": "2026-11-19T09:30:00+09:00",
          "clockOffsetMs": 0
        }
        """
            .formatted(INCIDENT_ID, OP_ID, TEAM_AREA_ID));
  }

  private static MockHttpServletRequestBuilder fcmTokenRegister() {
    return json(
        post("/api/fcm/tokens"),
        """
        { "appInstanceId": "app-instance-5101", "token": "fcm-token-5101" }
        """);
  }

  private static MockHttpServletRequestBuilder fcmTokenRevoke() {
    return delete("/api/fcm/tokens");
  }

  private static MockHttpServletRequestBuilder syncClock() {
    return json(
        post("/api/sync/clock"),
        """
        { "incidentId": "%s", "clientTs": "%s" }
        """
            .formatted(INCIDENT_ID, OffsetDateTime.now(ZoneOffset.UTC)));
  }

  private static MockHttpServletRequestBuilder outboxRequeue() {
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
    return json(
        post("/api/sync/outbox/requeue"),
        """
        {
          "operationId": "op-5101",
          "incidentId": "%s",
          "reason": "MANUAL_RETRY",
          "clientTs": "%s",
          "clockOffsetMs": 0,
          "clockSyncedAt": "%s",
          "attemptCount": 1
        }
        """
            .formatted(INCIDENT_ID, now, now));
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
