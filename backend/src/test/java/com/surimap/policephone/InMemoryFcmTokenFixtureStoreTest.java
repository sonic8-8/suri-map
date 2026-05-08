package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T04 in-memory FCM token fixture store")
class InMemoryFcmTokenFixtureStoreTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-08T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryPolicePhoneFixtureStore fixtureStore = new InMemoryPolicePhoneFixtureStore(clock);

  @Test
  @DisplayName("assigned police phone active token fixture를 조회한다")
  void returns_seeded_active_tokens_for_police_phone() {
    assertThat(fixtureStore.activeByPolicePhone(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.policePhoneId()).isEqualTo(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID);
              assertThat(row.appInstanceId()).isEqualTo(PolicePhoneFixtures.ASSIGNED_APP_INSTANCE_ID);
              assertThat(row.tokenCiphertext())
                  .isEqualTo("cipher:" + PolicePhoneFixtures.ASSIGNED_APP_TOKEN);
              assertThat(row.status()).isEqualTo(FcmTokenStatus.ACTIVE);
              assertThat(row.version()).isEqualTo(1L);
            });
  }

  @Test
  @DisplayName("같은 appInstanceId로 재등록하면 기존 ACTIVE를 대체하고 version을 증가시킨다")
  void rotates_active_token_per_app_instance() {
    Instant rotatedAt = Instant.parse("2026-05-08T00:05:00Z");

    var registered =
        fixtureStore.registerFcmToken(
            PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
            PolicePhoneFixtures.ASSIGNED_APP_INSTANCE_ID,
            PolicePhoneFixtures.ASSIGNED_APP_TOKEN_ROTATED,
            rotatedAt);

    assertThat(fixtureStore.activeByPolicePhone(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.id()).isEqualTo(registered.id());
              assertThat(row.tokenCiphertext())
                  .isEqualTo("cipher:" + PolicePhoneFixtures.ASSIGNED_APP_TOKEN_ROTATED);
              assertThat(row.status()).isEqualTo(FcmTokenStatus.ACTIVE);
              assertThat(row.version()).isEqualTo(2L);
            });
  }

  @Test
  @DisplayName("logout lifecycle는 matching account/policePhone active token을 무효화한다")
  void revokes_matching_active_tokens_on_logout() {
    fixtureStore.revokeActiveTokensForLogout(
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID, PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID);

    assertThat(fixtureStore.activeByPolicePhone(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID))
        .isEmpty();
  }

  @Test
  @DisplayName("unassigned police phone은 FCM token fixture 등록을 거부한다")
  void rejects_registration_for_unassigned_police_phone() {
    assertThatThrownBy(
            () ->
                fixtureStore.registerFcmToken(
                    PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID,
                    "app-instance-unassigned-301",
                    "fcm-token-unassigned-301"))
        .isInstanceOf(com.surimap.common.auth.guard.PolicePhoneNotAssignedException.class);
  }
}
