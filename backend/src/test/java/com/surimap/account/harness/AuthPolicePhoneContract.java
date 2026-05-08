package com.surimap.account.harness;

/** Adapter boundary for swapping L2 auth/policePhone mocks with the real S1-2 guard contract. */
public interface AuthPolicePhoneContract {

  AuthPolicePhoneHarnessFixtures.HarnessContext resolve(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture);

  AuthPolicePhoneHarnessFixtures.GuardOutcome checkAppPolicePhone(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture);

  AuthPolicePhoneHarnessFixtures.GuardOutcome checkWebCommand(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture);
}
