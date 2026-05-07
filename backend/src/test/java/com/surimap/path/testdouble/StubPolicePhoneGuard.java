package com.surimap.path.testdouble;

import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.port.PolicePhoneGuard;
import com.surimap.path.fixture.SearchPathFixtures;
import java.util.UUID;

public final class StubPolicePhoneGuard implements PolicePhoneGuard {

  @Override
  public void requireAssigned(UUID policePhoneId, UUID opId) {
    if (!SearchPathFixtures.POLICE_PHONE_ID.equals(policePhoneId)) {
      throw new SearchPathGuardException("police_phone_not_registered");
    }
    if (!SearchPathFixtures.OP1_ID.equals(opId)) {
      throw new SearchPathGuardException("police_phone_not_assigned");
    }
  }
}
