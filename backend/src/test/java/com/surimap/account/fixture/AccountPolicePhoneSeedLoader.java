package com.surimap.account.fixture;

import com.surimap.account.fixture.AccountPolicePhoneFixtures.AccountFixture;
import com.surimap.account.fixture.AccountPolicePhoneFixtures.PolicePhoneFixture;
import com.surimap.account.fixture.RoleChannelMatrixFixtures.RoleChannelRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Test seed loader for S1-2 auth/policePhone fixtures. */
public final class AccountPolicePhoneSeedLoader {

  private AccountPolicePhoneSeedLoader() {}

  public static AuthSeed load() {
    return new AuthSeed(
        AccountPolicePhoneFixtures.accounts(),
        AccountPolicePhoneFixtures.policePhones(),
        RoleChannelMatrixFixtures.rules());
  }

  public record AuthSeed(
      List<AccountFixture> accounts,
      List<PolicePhoneFixture> policePhones,
      List<RoleChannelRule> roleChannelRules) {

    public Optional<AccountFixture> accountById(UUID accountId) {
      return accounts.stream().filter(account -> account.id().equals(accountId)).findFirst();
    }

    public Optional<AccountFixture> accountByCode(String accountCode) {
      return accounts.stream()
          .filter(account -> account.accountCode().equals(accountCode))
          .findFirst();
    }

    public Optional<PolicePhoneFixture> policePhoneById(UUID policePhoneId) {
      return policePhones.stream()
          .filter(policePhone -> policePhone.id().equals(policePhoneId))
          .findFirst();
    }

    public Optional<PolicePhoneFixture> policePhoneByCode(String phoneCode) {
      return policePhones.stream()
          .filter(policePhone -> policePhone.phoneCode().equals(phoneCode))
          .findFirst();
    }
  }
}
