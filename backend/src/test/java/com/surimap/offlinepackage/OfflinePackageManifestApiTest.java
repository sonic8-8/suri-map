package com.surimap.offlinepackage;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.offlinepackage.service.OfflinePackageRepository;
import com.surimap.support.auth.WithMockAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "tileserver.mode=fixture")
class OfflinePackageManifestApiTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_A = UUID.fromString("10000000-0000-0000-0000-000000004701");
  private static final UUID INCIDENT_B = UUID.fromString("10000000-0000-0000-0000-000000004702");
  private static final String MANIFEST_A = "77777777-0000-4000-8000-000000004701";
  private static final String MANIFEST_B = "77777777-0000-4000-8000-000000004702";
  private static final String APP_A = "62000000-0000-0000-0000-000000004701";
  private static final String APP_A_PEER = "62000000-0000-0000-0000-000000004702";
  private static final String APP_B = "62000000-0000-0000-0000-000000004703";
  private static final String COMMANDER = "63000000-0000-0000-0000-000000004701";
  private static final String APP_A_PHONE = "50000000-0000-0000-0000-000000004701";
  private static final String APP_A_PEER_PHONE = "50000000-0000-0000-0000-000000004702";
  private static final String APP_B_PHONE = "50000000-0000-0000-0000-000000004703";
  private static final String UNREGISTERED_PHONE = "50000000-0000-0000-0000-000000004704";
  private static final String DISABLED_PHONE = "50000000-0000-0000-0000-000000004705";
  private static final String UNKNOWN_PHONE = "50000000-0000-0000-0000-000000004799";
  private static final String READY_HASH = "1".repeat(64);
  private static final String PURGED_HASH = "0".repeat(64);
  private static final Instant BASE_TS = Instant.parse("2026-11-16T00:00:00Z");

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void seedIncidents() {
    deleteSeededRows();
    insertAccount(APP_A, "TEAM", "MISSING_TEAM");
    insertAccount(APP_A_PEER, "PATROL_CAR", "POLICE_SUBSTATION");
    insertAccount(APP_B, "TEAM", "MISSING_TEAM");
    insertAccount(COMMANDER, "COMMAND", "POLICE_SUBSTATION");
    // 사건 A에는 지휘 계정과 같은 조직 유형의 계정이 배정돼 있다. 사건 B에는 그런 배정이 없다.
    insertIncident(INCIDENT_A);
    insertIncident(INCIDENT_B);
    insertAssignmentAndPhone(INCIDENT_A, APP_A, APP_A_PHONE, "4701");
    insertAssignmentAndPhone(INCIDENT_A, APP_A_PEER, APP_A_PEER_PHONE, "4702");
    insertAssignmentAndPhone(INCIDENT_B, APP_B, APP_B_PHONE, "4703");
    // 앱 계정 A에 연결된 업무폰이지만 등록되지 않았거나 비활성이다.
    insertPolicePhone(UNREGISTERED_PHONE, APP_A, "phone-4704", "ACTIVE", false);
    insertPolicePhone(DISABLED_PHONE, APP_A, "phone-4705", "DISABLED", true);
    // 사건 A만 오프라인 패키지가 준비돼 있다.
    insertManifest(MANIFEST_A, INCIDENT_A, READY_HASH);
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("A 사건에 배정된 앱 계정이 B 사건의 manifest를 요청하면 incident_access_denied로 거절된다")
  void app_account_assigned_to_other_incident_is_denied() throws Exception {
    // given: 사건 B도 오프라인 패키지가 준비돼 있다.
    insertManifest(MANIFEST_B, INCIDENT_B, READY_HASH);

    // when & then: 배정되지 않은 사건 B의 manifest를 자기 계정에 연결된 업무폰 ID로 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_B).param("policePhoneId", APP_A_PHONE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")))
        .andExpect(jsonPath("$.missingPerson").doesNotExist());
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB)
  @DisplayName("지휘 계정이 조직 범위 밖의 사건을 웹으로 요청하면 incident_access_denied로 거절된다")
  void command_account_outside_organization_scope_is_denied() throws Exception {
    // given: 사건 B도 오프라인 패키지가 준비돼 있다.
    insertManifest(MANIFEST_B, INCIDENT_B, READY_HASH);

    // when & then: 같은 조직 유형의 배정이 없는 사건 B를 업무폰 ID 없이 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_B))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")))
        .andExpect(jsonPath("$.missingPerson").doesNotExist());
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PEER_PHONE)
  @DisplayName("업무폰을 넘겨받은 앱 계정이 그 업무폰 ID로 준비된 자기 사건을 요청하면 manifest를 받는다")
  void assigned_app_account_with_handed_over_police_phone_receives_manifest() throws Exception {
    // given: 업무폰 APP_A_PEER_PHONE의 police_phone.account_id는 APP_A_PEER다.
    // when & then: 같은 사건에 배정된 앱 계정 A가 그 업무폰을 넘겨받아 자기 사건 A를 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_A).param("policePhoneId", APP_A_PEER_PHONE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.manifestId", is(MANIFEST_A)))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_A.toString())))
        .andExpect(jsonPath("$.policePhoneContext.policePhoneId", is(APP_A_PEER_PHONE)))
        .andExpect(jsonPath("$.missingPerson.displayName").exists());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "등록되지 않은 업무폰, " + UNREGISTERED_PHONE,
    "비활성 업무폰, " + DISABLED_PHONE,
    "없는 업무폰 ID, " + UNKNOWN_PHONE
  })
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("업무폰의 등록 상태와 관계없이 배정된 앱 계정은 자기 사건 manifest를 받는다")
  void unregistered_or_disabled_or_unknown_police_phone_receives_manifest(
      String description, String policePhoneId) throws Exception {
    // when & then: 사건 A에 배정된 앱 계정 A가 그 업무폰 ID로 자기 사건 A를 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_A).param("policePhoneId", policePhoneId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.manifestId", is(MANIFEST_A)))
        .andExpect(jsonPath("$.missingPerson.displayName").exists());
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"dev-precinct-phone-01", "1-1-1-1-1"})
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("36자 UUID 형식이 아닌 업무폰 ID로 manifest를 요청하면 police_phone_required로 거절된다")
  void police_phone_code_is_denied(String policePhoneId) throws Exception {
    // when & then: 사건 A에 배정된 앱 계정 A가 형식이 틀린 업무폰 ID로 자기 사건 A를 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_A).param("policePhoneId", policePhoneId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")))
        .andExpect(jsonPath("$.missingPerson").doesNotExist());
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("배정된 앱 계정이 자기 계정에 연결된 업무폰 ID로 준비된 자기 사건을 요청하면 manifest를 받는다")
  void assigned_app_account_with_linked_police_phone_receives_manifest() throws Exception {
    // when & then: 자기 사건 A를 자기 계정에 연결된 업무폰 ID로 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_A).param("policePhoneId", APP_A_PHONE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.manifestId", is(MANIFEST_A)))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_A.toString())))
        .andExpect(jsonPath("$.policePhoneContext.policePhoneId", is(APP_A_PHONE)))
        .andExpect(jsonPath("$.missingPerson.displayName").exists());
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB)
  @DisplayName("지휘 계정은 개인 배정과 업무폰 ID가 없어도 조직 범위 안의 준비된 사건 manifest를 웹으로 받는다")
  void command_account_in_organization_scope_receives_manifest_without_police_phone()
      throws Exception {
    // when & then: 같은 조직 유형의 계정이 배정된 사건 A를 업무폰 ID 없이 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.manifestId", is(MANIFEST_A)))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_A.toString())))
        // 업무폰 ID가 없으면 지금처럼 고정 업무폰으로 조립된다.
        .andExpect(
            jsonPath(
                "$.policePhoneContext.policePhoneId", is(OfflinePackageRepository.POLICE_PHONE_ID)))
        .andExpect(jsonPath("$.missingPerson.displayName").exists());
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("사건 권한이 없는 계정은 오프라인 패키지가 준비되지 않은 사건도 package_manifest_not_ready가 아니라 incident_access_denied로 거절된다")
  void unauthorized_account_is_denied_before_manifest_not_ready() throws Exception {
    // when & then: 오프라인 패키지가 없는 사건 B를 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_B).param("policePhoneId", APP_A_PHONE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")));
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("사건 권한이 없는 계정은 오프라인 패키지가 파기된 사건도 package_purged가 아니라 incident_access_denied로 거절된다")
  void unauthorized_account_is_denied_before_package_purged() throws Exception {
    // given: 사건 B의 오프라인 패키지가 파기됐다.
    insertManifest(MANIFEST_B, INCIDENT_B, PURGED_HASH);

    // when & then: 파기된 사건 B를 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_B).param("policePhoneId", APP_A_PHONE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")));
  }

  @Test
  @WithMockAccount(accountId = APP_B, policePhoneId = APP_B_PHONE)
  @DisplayName("배정된 앱 계정은 오프라인 패키지가 준비되지 않은 자기 사건에서 지금처럼 package_manifest_not_ready를 받는다")
  void assigned_app_account_receives_manifest_not_ready() throws Exception {
    // when & then: 오프라인 패키지가 없는 자기 사건 B를 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_B).param("policePhoneId", APP_B_PHONE))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("package_manifest_not_ready")));
  }

  @Test
  @WithMockAccount(accountId = APP_B, policePhoneId = APP_B_PHONE)
  @DisplayName("배정된 앱 계정은 오프라인 패키지가 파기된 자기 사건에서 지금처럼 package_purged를 받는다")
  void assigned_app_account_receives_package_purged() throws Exception {
    // given: 사건 B의 오프라인 패키지가 파기됐다.
    insertManifest(MANIFEST_B, INCIDENT_B, PURGED_HASH);

    // when & then: 파기된 자기 사건 B를 요청한다.
    mockMvc
        .perform(manifestRequest(INCIDENT_B).param("policePhoneId", APP_B_PHONE))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.error", is("package_purged")));
  }

  // 다른 PostGIS 테스트가 쓰는 기본 seed 계정을 지우지 않도록, 이 테스트가 넣은 행만 지운다.
  private void deleteSeededRows() {
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
        "DELETE FROM police_phone WHERE id IN (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid)",
        APP_A_PHONE,
        APP_A_PEER_PHONE,
        APP_B_PHONE,
        UNREGISTERED_PHONE,
        DISABLED_PHONE);
    jdbcTemplate.update(
        "DELETE FROM account WHERE id IN (?::uuid, ?::uuid, ?::uuid, ?::uuid)",
        APP_A,
        APP_A_PEER,
        APP_B,
        COMMANDER);
    jdbcTemplate.update(
        "DELETE FROM incident WHERE id IN (?::uuid, ?::uuid)",
        INCIDENT_A.toString(),
        INCIDENT_B.toString());
  }

  private void insertAccount(String accountId, String accountType, String organizationType) {
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES (?::uuid, ?, '{noop}fixture', ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        accountId,
        "acct-" + accountId,
        "account " + accountId,
        accountType,
        organizationType,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertIncident(UUID incidentId) {
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?::uuid, gen_random_uuid(), 'Offline package access incident', 'OPEN', ?, 1, ?, ?)
        """,
        incidentId.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  // 계정을 사건에 배정하고, police_phone.account_id가 그 계정인 등록된 업무폰을 한 대 넣는다.
  private void insertAssignmentAndPhone(
      UUID incidentId, String accountId, String policePhoneId, String suffix) {
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
    insertPolicePhone(policePhoneId, accountId, "phone-" + suffix, "ACTIVE", true);
  }

  private void insertPolicePhone(
      String policePhoneId, String accountId, String phoneCode, String status, boolean registered) {
    jdbcTemplate.update(
        """
        INSERT INTO police_phone (
          id, phone_code, display_name, account_id, status, registered,
          created_at, updated_at, version
        )
        VALUES (?::uuid, ?, 'Offline package access phone', ?::uuid, ?, ?, ?, ?, 1)
        """,
        policePhoneId,
        phoneCode,
        accountId,
        status,
        registered,
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

  private static MockHttpServletRequestBuilder manifestRequest(UUID incidentId) {
    return get("/api/incidents/{incidentId}/offline-package/manifest", incidentId);
  }
}
