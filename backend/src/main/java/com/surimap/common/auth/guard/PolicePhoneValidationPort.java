package com.surimap.common.auth.guard;

import java.util.UUID;

/**
 * Port for validating police phone assignment status.
 *
 * <p>Implementations throw the appropriate {@link GuardException} subclass on validation failure;
 * they return normally on success.
 */
public interface PolicePhoneValidationPort {

  /**
   * Checks that the police phone is assigned to an active incident.
   *
   * @throws PolicePhoneNotAssignedException if the phone is not assigned to an active incident
   */
  void checkAssigned(UUID policePhoneId);
}
