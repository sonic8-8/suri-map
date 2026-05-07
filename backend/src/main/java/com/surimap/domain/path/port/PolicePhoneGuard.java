package com.surimap.domain.path.port;

import java.util.UUID;

public interface PolicePhoneGuard {

  void requireAssigned(UUID policePhoneId, UUID opId);
}
