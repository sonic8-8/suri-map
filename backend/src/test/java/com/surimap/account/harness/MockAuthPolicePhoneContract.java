package com.surimap.account.harness;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.Role;

/** Pure in-memory L2 mock contract for cross-Lane auth/policePhone harness tests. */
public final class MockAuthPolicePhoneContract implements AuthPolicePhoneContract {

  @Override
  public AuthPolicePhoneHarnessFixtures.HarnessContext resolve(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture) {
    return fixture.context();
  }

  @Override
  public AuthPolicePhoneHarnessFixtures.GuardOutcome checkAppPolicePhone(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture) {
    if (fixture.context().channel() != Channel.APP) {
      return AuthPolicePhoneHarnessFixtures.GuardOutcome.denied("channel_not_allowed");
    }
    if (fixture.context().policePhoneId() == null) {
      return AuthPolicePhoneHarnessFixtures.GuardOutcome.denied("police_phone_required");
    }
    // 업무폰 등록 여부는 확인하지 않는다(ADR-0037).
    if (!fixture.policePhone().assigned()) {
      return AuthPolicePhoneHarnessFixtures.GuardOutcome.denied("police_phone_not_assigned");
    }
    return AuthPolicePhoneHarnessFixtures.GuardOutcome.ok();
  }

  @Override
  public AuthPolicePhoneHarnessFixtures.GuardOutcome checkWebCommand(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture) {
    if (fixture.context().channel() != Channel.WEB) {
      return AuthPolicePhoneHarnessFixtures.GuardOutcome.denied("channel_not_allowed");
    }
    boolean hasElevatedRole =
        fixture.context().authorities().stream().anyMatch(authority -> !Role.MEMBER.name().equals(authority));
    if (!hasElevatedRole) {
      return AuthPolicePhoneHarnessFixtures.GuardOutcome.denied("role_denied");
    }
    return AuthPolicePhoneHarnessFixtures.GuardOutcome.ok();
  }
}
