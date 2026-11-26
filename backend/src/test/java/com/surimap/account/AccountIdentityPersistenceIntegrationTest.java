package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.account.repository.AccountLoginMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("S1-2 account identity fixture persistence")
class AccountIdentityPersistenceIntegrationTest {

  @Autowired private AccountLoginMapper accountLoginMapper;

  @Test
  @DisplayName("account login lookup uses canonical DB fixtures")
  void accountLoginLookupUsesCanonicalDbFixtures() {
    var account =
        accountLoginMapper.findActiveAccountByLoginId(AccountIdentityCatalog.PRECINCT_TEAM_CODE);

    assertThat(account).isPresent();
    assertThat(account.orElseThrow().id()).isEqualTo(AccountIdentityCatalog.PRECINCT_TEAM_ID);
  }
}
