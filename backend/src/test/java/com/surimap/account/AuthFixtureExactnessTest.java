package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.account.fixture.AccountPolicePhoneFixtures;
import com.surimap.account.fixture.AccountPolicePhoneFixtures.AccountFixture;
import com.surimap.account.fixture.AccountPolicePhoneSeedLoader;
import com.surimap.account.fixture.RoleChannelMatrixFixtures;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthFixtureExactnessTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  @DisplayName("account and policePhone fixture IDs stay UUID while harness codes stay aliases")
  void account_and_policePhone_fixture_ids_stay_uuid_while_harness_codes_stay_aliases() {
    assertThat(AccountPolicePhoneFixtures.INCIDENT_ALIAS).isEqualTo("inc-precinct-first-001");
    assertThat(AccountPolicePhoneFixtures.OP1_ALIAS).isEqualTo("op-precinct-001-op1");

    assertThat(AccountPolicePhoneFixtures.accountIds())
        .containsExactlyInAnyOrder(
            AccountIdentityCatalog.PRECINCT_COMMANDER_ID,
            AccountIdentityCatalog.PRECINCT_PATROL_ID,
            AccountIdentityCatalog.PRECINCT_TEAM_ID,
            AccountIdentityCatalog.ALPHA_COMMANDER_ID,
            AccountIdentityCatalog.ALPHA_TEAM_ID,
            AccountIdentityCatalog.SUPPORT_COMMANDER_ID,
            AccountIdentityCatalog.SUPPORT_PATROL_ID,
            AccountIdentityCatalog.SUPPORT_TEAM_ID);

    assertThat(AccountPolicePhoneFixtures.policePhoneIds())
        .allSatisfy(id -> assertThat(id.toString()).matches("[0-9a-f-]{36}"));

    assertThat(AccountPolicePhoneFixtures.accountCodes())
        .containsExactlyInAnyOrder(
            "acct-precinct-cmd",
            "acct-precinct-car",
            "acct-precinct-team",
            "acct-cmd-alpha",
            "acct-team-alpha",
            "acct-support-cmd",
            "acct-support-car",
            "acct-support-team");

    assertThat(AccountPolicePhoneFixtures.policePhoneCodes())
        .containsExactlyInAnyOrder(
            "dev-precinct-cmd-phone-01",
            "dev-precinct-car-01",
            "dev-precinct-phone-01",
            "dev-alpha-cmd-phone-01",
            "dev-alpha-phone-01",
            "dev-support-cmd-phone-01",
            "dev-support-car-01",
            "dev-support-phone-01");
  }

  @Test
  @DisplayName("COMMANDER TEAM PATROL are fixture aliases mapped to real account types and roles")
  void fixture_aliases_map_to_real_account_types_and_roles() {
    assertThat(AccountPolicePhoneFixtures.precinctCommander().accountType())
        .isEqualTo(AccountType.COMMAND);
    assertThat(AccountPolicePhoneFixtures.precinctCommander().organizationType())
        .isEqualTo(OrganizationType.POLICE_SUBSTATION);
    assertThat(AccountPolicePhoneFixtures.precinctCommander().roles())
        .containsExactly(Role.FIELD_COMMANDER);

    assertThat(AccountPolicePhoneFixtures.alphaCommander().accountType())
        .isEqualTo(AccountType.COMMAND);
    assertThat(AccountPolicePhoneFixtures.alphaCommander().organizationType())
        .isEqualTo(OrganizationType.MISSING_TEAM);
    assertThat(AccountPolicePhoneFixtures.alphaCommander().roles())
        .containsExactlyInAnyOrder(Role.MISSING_TEAM_COMMANDER, Role.FIELD_COMMANDER);

    assertThat(AccountPolicePhoneFixtures.precinctTeam().accountType()).isEqualTo(AccountType.TEAM);
    assertThat(AccountPolicePhoneFixtures.precinctTeam().roles()).containsExactly(Role.MEMBER);
    assertThat(AccountPolicePhoneFixtures.precinctPatrol().accountType())
        .isEqualTo(AccountType.PATROL_CAR);
    assertThat(AccountPolicePhoneFixtures.precinctPatrol().roles()).containsExactly(Role.MEMBER);
  }

  @Test
  @DisplayName("test seed loader exposes account policePhone and role channel matrix fixtures")
  void test_seed_loader_exposes_auth_fixture_baseline() {
    AccountPolicePhoneSeedLoader.AuthSeed seed = AccountPolicePhoneSeedLoader.load();

    assertThat(seed.accounts()).hasSize(8);
    assertThat(seed.policePhones()).hasSize(8);
    assertThat(seed.roleChannelRules()).containsAll(RoleChannelMatrixFixtures.rules());
    assertThat(seed.accountByCode("acct-support-car").orElseThrow().accountType())
        .isEqualTo(AccountType.PATROL_CAR);
    assertThat(seed.policePhoneByCode("dev-support-phone-01").orElseThrow().accountId())
        .isEqualTo(AccountIdentityCatalog.SUPPORT_TEAM_ID);
  }

  @Test
  @DisplayName("role channel matrix fixture freezes APP WEB permission rows without guard behavior")
  void role_channel_matrix_fixture_freezes_boundaries_matrix_rows() {
    assertThat(RoleChannelMatrixFixtures.rules()).hasSize(14);
    assertThat(RoleChannelMatrixFixtures.rule("사건 가져오기").allowedChannels()).containsExactly("WEB");
    assertThat(RoleChannelMatrixFixtures.rule("수색 세션").allowedChannels()).containsExactly("APP");
    assertThat(RoleChannelMatrixFixtures.rule("마커 수정·삭제").allowedChannels())
        .containsExactlyInAnyOrder("APP", "WEB");
    assertThat(RoleChannelMatrixFixtures.rule("상황판 조회").requiredRoleText()).isEqualTo("사건 배정 계정");
  }

  @Test
  @DisplayName("Keycloak demo realm users match auth account and policePhone fixtures")
  void keycloak_demo_realm_users_match_auth_account_and_policePhone_fixtures() throws IOException {
    JsonNode realm =
        readJson(Path.of("..", "infra", "docker", "keycloak", "import", "suri-map-realm.json"));
    Map<String, JsonNode> usersByUsername =
        iterable(realm.path("users")).stream()
            .collect(Collectors.toMap(user -> text(user, "username"), Function.identity()));

    assertThat(usersByUsername.keySet())
        .containsExactlyInAnyOrderElementsOf(
            AccountPolicePhoneFixtures.accountsIncludingUnassigned().stream()
                .map(AccountFixture::accountCode)
                .toList());

    for (AccountFixture fixture : AccountPolicePhoneFixtures.accountsIncludingUnassigned()) {
      JsonNode user = usersByUsername.get(fixture.accountCode());
      assertThat(attribute(user, "accountId")).isEqualTo(fixture.id().toString());
      assertThat(attribute(user, "accountType")).isEqualTo(fixture.accountType().name());
      assertThat(attribute(user, "organizationType")).isEqualTo(fixture.organizationType().name());
      assertThat(attribute(user, "policePhoneId")).isEqualTo(fixture.policePhoneId().toString());
      assertThat(attribute(user, "policePhoneCode")).isEqualTo(fixture.policePhoneCode());
      assertThat(textSet(user.path("realmRoles")))
          .containsExactlyInAnyOrderElementsOf(
              fixture.roles().stream().map(Role::name).collect(Collectors.toSet()));
    }
  }

  @Test
  @DisplayName("production account policePhone seed keeps auth fixture IDs and aliases")
  void production_account_policePhone_seed_keeps_auth_fixture_ids_and_aliases() throws IOException {
    String seedSql =
        Files.readString(
                Path.of(
                    "src",
                    "main",
                    "resources",
                    "db",
                    "migration",
                    "V20260513_006__ensure_account_police_phone_fixtures.sql"))
            + "\n"
            + Files.readString(
                Path.of(
                    "src",
                    "main",
                    "resources",
                    "db",
                    "migration",
                    "V20260513_008__police_phone_db_persistence_state.sql"));

    for (AccountFixture fixture : AccountPolicePhoneFixtures.accountsIncludingUnassigned()) {
      assertThat(seedSql).contains("'" + fixture.id() + "'");
      assertThat(seedSql).contains("'" + fixture.accountCode() + "'");
      assertThat(seedSql).contains("'" + fixture.accountType().name() + "'");
      assertThat(seedSql).contains("'" + fixture.organizationType().name() + "'");
      assertThat(seedSql).contains("'" + fixture.policePhoneId() + "'");
      assertThat(seedSql).contains("'" + fixture.policePhoneCode() + "'");
    }
  }

  private static JsonNode readJson(Path path) throws IOException {
    return OBJECT_MAPPER.readTree(path.toFile());
  }

  private static java.util.List<JsonNode> iterable(JsonNode node) {
    assertThat(node.isArray()).isTrue();
    java.util.List<JsonNode> values = new java.util.ArrayList<>();
    node.forEach(values::add);
    return values;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    assertThat(value.isTextual()).isTrue();
    return value.asText();
  }

  private static String attribute(JsonNode user, String name) {
    JsonNode values = user.path("attributes").path(name);
    assertThat(values.isArray()).isTrue();
    assertThat(values).hasSize(1);
    assertThat(values.get(0).isTextual()).isTrue();
    return values.get(0).asText();
  }

  private static Set<String> textSet(JsonNode node) {
    assertThat(node.isArray()).isTrue();
    return iterable(node).stream().map(JsonNode::asText).collect(Collectors.toSet());
  }
}
