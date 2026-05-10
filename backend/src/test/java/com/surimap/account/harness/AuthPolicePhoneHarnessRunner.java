package com.surimap.account.harness;

/** Small runner other Lane tests can use to execute the canonical S1-2 harness fixture. */
public final class AuthPolicePhoneHarnessRunner {

  private final AuthPolicePhoneHarnessFixtures.HarnessContext context;
  private final AuthPolicePhoneHarnessFixtures.PolicePhoneAssignment policePhone;
  private final AuthPolicePhoneHarnessFixtures.GuardOutcome appPolicePhoneOutcome;

  private AuthPolicePhoneHarnessRunner(
      AuthPolicePhoneHarnessFixtures.HarnessContext context,
      AuthPolicePhoneHarnessFixtures.PolicePhoneAssignment policePhone,
      AuthPolicePhoneHarnessFixtures.GuardOutcome appPolicePhoneOutcome) {
    this.context = context;
    this.policePhone = policePhone;
    this.appPolicePhoneOutcome = appPolicePhoneOutcome;
  }

  public static AuthPolicePhoneHarnessRunner mock(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture) {
    return run(new MockAuthPolicePhoneContract(), fixture);
  }

  public static AuthPolicePhoneHarnessRunner run(
      AuthPolicePhoneContract contract, AuthPolicePhoneHarnessFixtures.HarnessFixture fixture) {
    return new AuthPolicePhoneHarnessRunner(
        contract.resolve(fixture), fixture.policePhone(), contract.checkAppPolicePhone(fixture));
  }

  public AuthPolicePhoneHarnessFixtures.HarnessContext context() {
    return context;
  }

  public AuthPolicePhoneHarnessFixtures.PolicePhoneAssignment policePhone() {
    return policePhone;
  }

  public AuthPolicePhoneHarnessFixtures.GuardOutcome appPolicePhoneOutcome() {
    return appPolicePhoneOutcome;
  }
}
