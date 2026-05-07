package com.surimap.common.auth.guard;

import com.surimap.common.auth.SuriMapAuthentication;

/**
 * Port for checking whether an authenticated account has access to the active incident.
 *
 * <p>Implementations throw {@link IncidentAccessDeniedException} or {@link
 * TeamNotAssignedException} on rejection; they return normally on success.
 */
public interface IncidentAccessPort {

  /**
   * Checks incident access for the given authentication.
   *
   * @throws IncidentAccessDeniedException if the account's organization type is not allowed
   * @throws TeamNotAssignedException if a MISSING_TEAM account is not yet assigned to an incident
   */
  void checkAccess(SuriMapAuthentication auth);
}
