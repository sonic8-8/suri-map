package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.account.harness.AuthPolicePhoneContract;
import com.surimap.account.harness.AuthPolicePhoneHarnessFixtures;
import com.surimap.account.harness.AuthPolicePhoneHarnessRunner;
import com.surimap.account.harness.MockAuthPolicePhoneContract;
import com.surimap.account.harness.RealS1_2AuthPolicePhoneContract;
import com.surimap.common.auth.Channel;
import com.surimap.policephone.InMemoryPolicePhoneFixtureStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T09A auth/policePhone harness runner RED")
class AuthPolicePhoneHarnessRunnerRedTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-08T00:00:00Z"), ZoneOffset.UTC);

  @Test
  @DisplayName("runner exposes role channel and policePhone assignment mocks")
  void runner_exposes_role_channel_and_police_phone_assignment_mocks() {
    AuthPolicePhoneHarnessRunner runner =
        AuthPolicePhoneHarnessRunner.mock(AuthPolicePhoneHarnessFixtures.precinctTeamApp());

    assertThat(runner.context().accountId()).isEqualTo("acct-precinct-team");
    assertThat(runner.context().channel()).isEqualTo(Channel.APP);
    assertThat(runner.context().authorities()).containsExactly("MEMBER");
    assertThat(runner.policePhone().alias()).isEqualTo("dev-precinct-phone-01");
    assertThat(runner.policePhone().policePhoneId())
        .isEqualTo(AuthPolicePhoneHarnessFixtures.PRECINCT_TEAM_POLICE_PHONE_ID);
    assertThat(runner.policePhone().registered()).isTrue();
    assertThat(runner.policePhone().assigned()).isTrue();
    assertThat(runner.appPolicePhoneOutcome().permitted()).isTrue();
  }

  @Test
  @DisplayName("mock and real S1-2 contracts swap without fixture ID drift")
  void mock_and_real_contracts_swap_without_fixture_id_drift() {
    AuthPolicePhoneContract mock = new MockAuthPolicePhoneContract();
    AuthPolicePhoneContract real =
        new RealS1_2AuthPolicePhoneContract(new InMemoryPolicePhoneFixtureStore(clock));

    var fixture = AuthPolicePhoneHarnessFixtures.precinctTeamApp();

    assertThat(mock.resolve(fixture)).isEqualTo(real.resolve(fixture));
    assertThat(mock.checkAppPolicePhone(fixture)).isEqualTo(real.checkAppPolicePhone(fixture));
  }

  @Test
  @DisplayName("mock and real S1-2 contracts expose the same role channel guard outcomes")
  void mock_and_real_contracts_expose_same_role_channel_guard_outcomes() {
    AuthPolicePhoneContract mock = new MockAuthPolicePhoneContract();
    AuthPolicePhoneContract real =
        new RealS1_2AuthPolicePhoneContract(new InMemoryPolicePhoneFixtureStore(clock));

    assertThat(mock.checkWebCommand(AuthPolicePhoneHarnessFixtures.alphaCommanderWeb()))
        .isEqualTo(real.checkWebCommand(AuthPolicePhoneHarnessFixtures.alphaCommanderWeb()));
    assertThat(mock.checkWebCommand(AuthPolicePhoneHarnessFixtures.precinctTeamApp()))
        .isEqualTo(real.checkWebCommand(AuthPolicePhoneHarnessFixtures.precinctTeamApp()));
    assertThat(mock.checkWebCommand(AuthPolicePhoneHarnessFixtures.precinctTeamWeb()))
        .isEqualTo(real.checkWebCommand(AuthPolicePhoneHarnessFixtures.precinctTeamWeb()));
  }

  @Test
  @DisplayName("mock and real S1-2 contracts expose the same app policePhone failure codes")
  void mock_and_real_contracts_expose_same_app_police_phone_failure_codes() {
    AuthPolicePhoneContract mock = new MockAuthPolicePhoneContract();
    AuthPolicePhoneContract real =
        new RealS1_2AuthPolicePhoneContract(new InMemoryPolicePhoneFixtureStore(clock));

    assertThat(mock.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.precinctTeamApp()))
        .isEqualTo(real.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.precinctTeamApp()));
    assertThat(mock.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.missingPolicePhoneApp()))
        .isEqualTo(real.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.missingPolicePhoneApp()));
    assertThat(mock.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.unregisteredPolicePhoneApp()))
        .isEqualTo(
            real.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.unregisteredPolicePhoneApp()));
    assertThat(mock.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.unassignedPolicePhoneApp()))
        .isEqualTo(
            real.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.unassignedPolicePhoneApp()));
    assertThat(mock.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.precinctTeamWeb()))
        .isEqualTo(real.checkAppPolicePhone(AuthPolicePhoneHarnessFixtures.precinctTeamWeb()));
  }

  @Test
  @DisplayName("app-police-phone failure precedence: compound violation returns first error only (channel_not_allowed)")
  void app_police_phone_failure_precedence_compound_violation_returns_first_error_only() {
    AuthPolicePhoneContract mock = new MockAuthPolicePhoneContract();
    AuthPolicePhoneContract real =
        new RealS1_2AuthPolicePhoneContract(new InMemoryPolicePhoneFixtureStore(clock));

    var compoundViolation = AuthPolicePhoneHarnessFixtures.webChannelMissingPolicePhone();

    assertThat(mock.checkAppPolicePhone(compoundViolation))
        .isEqualTo(real.checkAppPolicePhone(compoundViolation));
    assertThat(mock.checkAppPolicePhone(compoundViolation))
        .isEqualTo(AuthPolicePhoneHarnessFixtures.GuardOutcome.denied("channel_not_allowed"));
  }
}
