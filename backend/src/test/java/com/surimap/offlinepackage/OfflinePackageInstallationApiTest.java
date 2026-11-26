package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "tileserver.mode=fixture")
class OfflinePackageInstallationApiTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_A = UUID.fromString("10000000-0000-0000-0000-000000004801");
  private static final UUID INCIDENT_B = UUID.fromString("10000000-0000-0000-0000-000000004802");
  private static final String MANIFEST_A = "77777777-0000-4000-8000-000000004801";
  private static final String MANIFEST_B = "77777777-0000-4000-8000-000000004802";
  private static final String APP_A = "62000000-0000-0000-0000-000000004801";
  private static final String APP_A_PEER = "62000000-0000-0000-0000-000000004802";
  private static final String APP_B = "62000000-0000-0000-0000-000000004803";
  private static final String APP_A_PHONE = "50000000-0000-0000-0000-000000004801";
  private static final String APP_A_PEER_PHONE = "50000000-0000-0000-0000-000000004802";
  private static final String APP_B_PHONE = "50000000-0000-0000-0000-000000004803";
  private static final String READY_HASH = "1".repeat(64);
  private static final String PURGED_HASH = "0".repeat(64);
  private static final String IDEMPOTENCY_KEY_PREFIX = "idem-48-";
  private static final Instant BASE_TS = Instant.parse("2026-11-16T00:00:00Z");

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void seedIncidents() {
    deleteSeededRows();
    insertAccount(APP_A);
    insertAccount(APP_A_PEER);
    insertAccount(APP_B);
    insertIncident(INCIDENT_A);
    insertIncident(INCIDENT_B);
    insertAssignment(INCIDENT_A, APP_A, "4801");
    insertAssignment(INCIDENT_A, APP_A_PEER, "4802");
    insertAssignment(INCIDENT_B, APP_B, "4803");
    insertManifest(MANIFEST_A, INCIDENT_A, READY_HASH);
    // 사건마다 다른 계정이 쓰던 업무폰의 PARTIAL 설치 상태가 한 행씩 있다.
    insertPartialInstallation(MANIFEST_A, APP_A_PEER_PHONE);
    insertPartialInstallation(MANIFEST_B, APP_B_PHONE);
  }

  @AfterEach
  void clearAuthentication() {
    TestSecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("A 사건에 배정된 앱 계정이 B 사건의 설치 보고를 보내면 incident_access_denied로 거절돼 설치 상태 행이 바뀌지 않는다")
  void app_account_assigned_to_other_incident_is_denied() throws Exception {
    // given: 사건 B도 오프라인 패키지가 준비돼 있다.
    insertManifest(MANIFEST_B, INCIDENT_B, READY_HASH);
    List<Map<String, Object>> before = installationRows(MANIFEST_B);

    // when & then: 앱 계정 A가 배정되지 않은 사건 B의 설치 보고를 자기가 쓰는 업무폰으로 보낸다.
    report(APP_A, INCIDENT_B.toString(), APP_A_PHONE, MANIFEST_B, "idem-48-cross-incident")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")))
        .andExpect(jsonPath("$.id").doesNotExist());

    // then: 사건 B의 설치 상태 행과 요청 처리 기록은 그대로다.
    assertThat(installationRows(MANIFEST_B)).isEqualTo(before);
    assertThat(idempotencyRowCount()).isZero();
  }

  @Test
  @DisplayName("업무폰을 넘겨받은 앱 계정이 그 업무폰으로 자기 사건의 설치 보고를 보내면 지금처럼 설치 상태가 저장된다")
  void assigned_app_account_with_handed_over_police_phone_saves_installation() throws Exception {
    // given: APP_A_PEER가 쓰던 업무폰 APP_A_PEER_PHONE에는 PARTIAL 설치 상태가 있다.
    // when & then: 같은 사건에 배정된 앱 계정 A가 그 업무폰을 넘겨받아 설치 보고를 보낸다.
    String installationId =
        JsonPath.read(
            report(APP_A, INCIDENT_A.toString(), APP_A_PEER_PHONE, MANIFEST_A, "idem-48-handed-over")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.version", is(3)))
                .andExpect(jsonPath("$.manifestVersion", is(1)))
                .andExpect(jsonPath("$.readyForOfflineUse", is(true)))
                .andExpect(jsonPath("$.serverTs").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    // then: 그 업무폰의 PARTIAL 행이 응답 ID의 READY 행으로 바뀐다.
    assertThat(installationRows(MANIFEST_A))
        .extracting(
            row -> row.get("id"), row -> row.get("police_phone_id"), row -> row.get("status"))
        .containsExactly(tuple(installationId, APP_A_PEER_PHONE, "READY"));
  }

  @Test
  @DisplayName("같은 Idempotency-Key의 저장된 응답이 있어도 사건 권한이 없는 앱 계정의 설치 보고는 incident_access_denied로 거절된다")
  void stored_response_is_not_replayed_to_app_account_without_incident_access() throws Exception {
    // given: 사건 B에 배정된 앱 계정이 자기가 쓰는 업무폰으로 설치 보고를 보내 응답이 저장됐다.
    insertManifest(MANIFEST_B, INCIDENT_B, READY_HASH);
    report(APP_B, INCIDENT_B.toString(), APP_B_PHONE, MANIFEST_B, "idem-48-replay-incident")
        .andExpect(status().isOk());
    List<Map<String, Object>> before = installationRows(MANIFEST_B);

    // when & then: 앱 계정 A가 같은 Idempotency-Key와 본문으로 다시 보낸다.
    report(APP_A, INCIDENT_B.toString(), APP_B_PHONE, MANIFEST_B, "idem-48-replay-incident")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")))
        .andExpect(jsonPath("$.id").doesNotExist());

    // then: 처음 보고한 결과만 남는다.
    assertThat(installationRows(MANIFEST_B)).isEqualTo(before);
    assertThat(idempotencyRowCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("배정된 앱 계정이 자기가 쓰는 업무폰으로 자기 사건의 설치 보고를 보내면 지금처럼 설치 상태가 저장된다")
  void assigned_app_account_with_linked_police_phone_saves_installation() throws Exception {
    // when & then: 앱 계정 A가 자기 사건 A의 설치 보고를 자기가 쓰는 업무폰으로 보낸다.
    String installationId =
        JsonPath.read(
            report(APP_A, INCIDENT_A.toString(), APP_A_PHONE, MANIFEST_A, "idem-48-own")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.version", is(3)))
                .andExpect(jsonPath("$.manifestVersion", is(1)))
                .andExpect(jsonPath("$.readyForOfflineUse", is(true)))
                .andExpect(jsonPath("$.serverTs").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    // then: 자기가 쓰는 업무폰의 READY 행이 더해진다. 다른 계정이 쓰던 업무폰의 PARTIAL 행은 그대로다.
    assertThat(installationRows(MANIFEST_A))
        .extracting(row -> row.get("police_phone_id"), row -> row.get("status"))
        .containsExactlyInAnyOrder(tuple(APP_A_PHONE, "READY"), tuple(APP_A_PEER_PHONE, "PARTIAL"));
    assertThat(installationRows(MANIFEST_A))
        .filteredOn(row -> APP_A_PHONE.equals(row.get("police_phone_id")))
        .singleElement()
        .satisfies(row -> assertThat(row).containsEntry("id", installationId));
  }

  @Test
  @DisplayName("사건 권한이 없는 계정은 오프라인 패키지가 파기된 사건에도 incident_closed가 아니라 incident_access_denied로 거절된다")
  void unauthorized_account_is_denied_before_package_purged() throws Exception {
    // given: 사건 B의 오프라인 패키지가 파기됐다.
    insertManifest(MANIFEST_B, INCIDENT_B, PURGED_HASH);

    // when & then: 앱 계정 A가 파기된 사건 B의 설치 보고를 보낸다.
    report(APP_A, INCIDENT_B.toString(), APP_A_PHONE, MANIFEST_B, "idem-48-purged")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")));
  }

  @Test
  @DisplayName("종료됐지만 패키지가 파기되지 않은 사건에 현재 manifest로 새 Idempotency-Key의 설치 보고를 보내면 incident_closed로 거절돼 설치 상태 행이 바뀌지 않는다")
  void new_report_for_closed_incident_before_purge_is_rejected_with_incident_closed()
      throws Exception {
    // given: 앱 계정 A가 배정된 사건 A가 종료됐다. 사건 A의 현재 manifest는 파기되지 않았다.
    closeIncident(INCIDENT_A);
    List<Map<String, Object>> before = installationRows(MANIFEST_A);

    // when & then: 앱 계정 A가 현재 manifest로 새 Idempotency-Key의 설치 보고를 보낸다.
    report(APP_A, INCIDENT_A.toString(), APP_A_PHONE, MANIFEST_A, "idem-48-closed-new")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("incident_closed")))
        .andExpect(jsonPath("$.id").doesNotExist());

    // then: 설치 상태 행과 요청 처리 기록이 바뀌지 않는다.
    assertThat(installationRows(MANIFEST_A)).isEqualTo(before);
    assertThat(idempotencyRowCount()).isZero();
  }

  @Test
  @DisplayName("종료 전에 저장된 같은 Idempotency-Key의 설치 보고 응답은 사건이 종료된 뒤에도 그대로 돌아온다")
  void stored_response_before_close_is_replayed_after_close() throws Exception {
    // given: 앱 계정 A가 사건 A의 설치 보고를 보내 응답이 저장됐다. 그 뒤 사건 A가 종료됐다.
    String storedResponse =
        report(APP_A, INCIDENT_A.toString(), APP_A_PHONE, MANIFEST_A, "idem-48-closed-replay")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    closeIncident(INCIDENT_A);
    List<Map<String, Object>> before = installationRows(MANIFEST_A);

    // when & then: 앱 계정 A가 같은 Idempotency-Key와 본문으로 다시 보내면 저장된 응답이 돌아온다.
    report(APP_A, INCIDENT_A.toString(), APP_A_PHONE, MANIFEST_A, "idem-48-closed-replay")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(JsonPath.<String>read(storedResponse, "$.id"))))
        .andExpect(jsonPath("$.status", is("READY")));

    // then: 설치 상태 행과 요청 처리 기록이 바뀌지 않는다.
    assertThat(installationRows(MANIFEST_A)).isEqualTo(before);
    assertThat(idempotencyRowCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("UUID 형식이 아닌 사건 별칭으로 설치 보고를 보내면 incident_access_denied로 거절된다")
  void incident_alias_is_denied() throws Exception {
    // when & then: 개발용 사건 별칭으로 설치 보고를 보낸다.
    report(APP_A, "inc-precinct-first-001", APP_A_PHONE, MANIFEST_A, "idem-48-incident-alias")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")));

    // then: 요청 처리 기록이 남지 않는다.
    assertThat(idempotencyRowCount()).isZero();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"dev-precinct-phone-01", "1-1-1-1-1"})
  @DisplayName("36자 UUID 형식이 아닌 업무폰 ID로 설치 보고를 보내면 police_phone_required로 거절된다")
  void police_phone_code_is_denied(String policePhoneId) throws Exception {
    // when & then: 자기 사건 A의 설치 보고를 형식이 틀린 업무폰 ID로 보낸다.
    report(APP_A, INCIDENT_A.toString(), policePhoneId, MANIFEST_A, "idem-48-phone-code")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));

    // then: 요청 처리 기록이 남지 않는다.
    assertThat(idempotencyRowCount()).isZero();
  }

  // 작업자는 다른 PostGIS 테스트가 쓰는 기본 seed 계정이 지워지지 않도록, 이 테스트가 넣은 행만 지우게 했다.
  private void deleteSeededRows() {
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key LIKE ?",
        IDEMPOTENCY_KEY_PREFIX + "%");
    jdbcTemplate.update(
        "DELETE FROM offline_package_installation WHERE offline_package_manifest_id IN (?::uuid, ?::uuid)",
        MANIFEST_A,
        MANIFEST_B);
    jdbcTemplate.update(
        "DELETE FROM offline_package_manifest WHERE incident_id IN (?::uuid, ?::uuid)",
        INCIDENT_A.toString(),
        INCIDENT_B.toString());
    jdbcTemplate.update(
        "DELETE FROM incident_assignment WHERE incident_id IN (?::uuid, ?::uuid)",
        INCIDENT_A.toString(),
        INCIDENT_B.toString());
    jdbcTemplate.update(
        "DELETE FROM account WHERE id IN (?::uuid, ?::uuid, ?::uuid)", APP_A, APP_A_PEER, APP_B);
    jdbcTemplate.update(
        "DELETE FROM incident WHERE id IN (?::uuid, ?::uuid)",
        INCIDENT_A.toString(),
        INCIDENT_B.toString());
  }

  private void insertAccount(String accountId) {
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES (?::uuid, ?, '{noop}fixture', ?, 'TEAM', 'MISSING_TEAM', 'ACTIVE', ?, ?)
        """,
        accountId,
        "acct-" + accountId,
        "account " + accountId,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertIncident(UUID incidentId) {
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?::uuid, gen_random_uuid(), 'Offline package installation incident', 'OPEN', ?, 1, ?, ?)
        """,
        incidentId.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void closeIncident(UUID incidentId) {
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = ? WHERE id = ?::uuid",
        Timestamp.from(BASE_TS.plusSeconds(3_600)),
        incidentId.toString());
  }

  private void insertAssignment(UUID incidentId, String accountId, String suffix) {
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, 'MEMBER', ?, ?, ?)
        """,
        "61000000-0000-0000-0000-00000000" + suffix,
        incidentId.toString(),
        accountId,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertManifest(String manifestId, UUID incidentId, String manifestHash) {
    jdbcTemplate.update(
        """
        INSERT INTO offline_package_manifest (
          id, incident_id, manifest_version, operational_period_id, overall_search_area_id,
          overall_search_area_version, manifest_hash, manifest_format_version, manifest_payload,
          expires_at, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, 1, gen_random_uuid(), gen_random_uuid(), 1, ?, 1, '{}'::jsonb,
                ?, ?, ?)
        """,
        manifestId,
        incidentId.toString(),
        manifestHash,
        Timestamp.from(BASE_TS.plusSeconds(86_400 * 365)),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertPartialInstallation(String manifestId, String policePhoneId) {
    jdbcTemplate.update(
        """
        INSERT INTO offline_package_installation (
          id, offline_package_manifest_id, police_phone_id, status, total_item_count,
          completed_item_count, failed_item_count, last_reported_at, version, created_at, updated_at
        )
        VALUES (gen_random_uuid(), ?::uuid, ?::uuid, 'PARTIAL', 7, 5, 2, ?, 2, ?, ?)
        """,
        manifestId,
        policePhoneId,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private List<Map<String, Object>> installationRows(String manifestId) {
    return jdbcTemplate.queryForList(
        """
        SELECT id::text AS id, police_phone_id::text AS police_phone_id, status,
               completed_item_count, version, last_reported_at, updated_at
        FROM offline_package_installation
        WHERE offline_package_manifest_id = ?::uuid
        ORDER BY police_phone_id
        """,
        manifestId);
  }

  // 작업자는 한 테스트 안에서 계정을 바꿔 요청하려고, 요청마다 토큰 계정을 넣었다.
  // 헤더와 본문에는 같은 업무폰 ID를 넣었다.
  private ResultActions report(
      String accountId,
      String incidentId,
      String policePhoneId,
      String manifestId,
      String idempotencyKey)
      throws Exception {
    TestSecurityContextHolder.setAuthentication(
        new SuriMapAuthentication(
            accountId, AccountType.TEAM, OrganizationType.MISSING_TEAM, Channel.APP, null, List.of()));
    return mockMvc.perform(
        post("/api/incidents/{incidentId}/offline-package/installations", incidentId)
            .header("Authorization", "Bearer app-package-session")
            .header("X-Client-Channel", "APP")
            .header("X-PolicePhone-Id", policePhoneId)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(readyReportJson(policePhoneId, manifestId)));
  }

  private static String readyReportJson(String policePhoneId, String manifestId) {
    return """
        {
          "policePhoneId": "%s",
          "manifestId": "%s",
          "manifestVersion": 1,
          "status": "READY",
          "totalItems": 7,
          "completedItems": 7,
          "failedItems": 0,
          "version": 3,
          "clientTs": "2026-11-16T09:00:00+09:00",
          "readyForOfflineUse": true,
          "failedItemKeys": [],
          "sequence": 901,
          "clockOffsetMs": 0
        }
        """
        .formatted(policePhoneId, manifestId);
  }

  private int idempotencyRowCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key LIKE ?",
        Integer.class,
        IDEMPOTENCY_KEY_PREFIX + "%");
  }
}
