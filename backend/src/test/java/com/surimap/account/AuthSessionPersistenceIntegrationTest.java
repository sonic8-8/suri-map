package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.account.repository.AccountLoginMapper;
import com.surimap.common.auth.Channel;
import com.surimap.policephone.PolicePhoneFixtures;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("S1-2 auth session persistence")
class AuthSessionPersistenceIntegrationTest {

  @Autowired private AccountLoginMapper accountLoginMapper;
  @Autowired private Clock clock;

  @Test
  @DisplayName("account and policePhone login lookups use canonical DB fixtures")
  void accountAndPolicePhoneLoginLookupsUseCanonicalDbFixtures() {
    var account =
        accountLoginMapper.findActiveAccountByLoginId(AccountIdentityCatalog.PRECINCT_TEAM_CODE);
    var policePhone = accountLoginMapper.findActivePolicePhoneByCode("dev-precinct-phone-01");

    assertThat(account).isPresent();
    assertThat(account.orElseThrow().id()).isEqualTo(AccountIdentityCatalog.PRECINCT_TEAM_ID);
    assertThat(policePhone).isPresent();
    assertThat(policePhone.orElseThrow().id())
        .isEqualTo(PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID);
  }

  @Test
  @DisplayName("refresh_token stores bearer hash and revocation removes authentication")
  void refreshTokenStoresBearerHashAndRevocationRemovesAuthentication() {
    UUID sessionId = UUID.randomUUID();
    String tokenHash = hash("test-access-token");
    var now = clock.instant();

    accountLoginMapper.insertRefreshToken(
        sessionId,
        AccountIdentityCatalog.PRECINCT_TEAM_ID,
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
        Channel.APP,
        tokenHash,
        now.plus(Duration.ofDays(30)),
        now);

    assertThat(accountLoginMapper.findActiveSessionByTokenHash(tokenHash, now)).isPresent();
    assertThat(accountLoginMapper.revokeBySessionId(sessionId, now.plusSeconds(1))).isEqualTo(1);
    assertThat(accountLoginMapper.findActiveSessionByTokenHash(tokenHash, now.plusSeconds(2)))
        .isEmpty();
  }

  private static String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 must be available", exception);
    }
  }
}
