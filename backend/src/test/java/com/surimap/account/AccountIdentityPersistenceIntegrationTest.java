package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.account.repository.AccountLoginMapper;
import com.surimap.policephone.PolicePhoneFixtures;
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
}
