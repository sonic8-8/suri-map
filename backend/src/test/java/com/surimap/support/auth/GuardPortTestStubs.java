package com.surimap.support.auth;

import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.IncidentAccessDeniedException;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.common.auth.guard.PolicePhoneNotAssignedException;
import com.surimap.common.auth.guard.PolicePhoneNotRegisteredException;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.common.auth.guard.TeamNotAssignedException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Test-only stub port implementations for guard checks.
 *
 * <p>These stubs are active only when no real implementation is present in the application context.
 * They are keyed on sentinel UUIDs matching the test fixtures in GuardAliasRedTest.
 *
 * <p>This class must NOT be included in {@code src/main}; it lives exclusively in the test source
 * tree so that production builds always require a real port implementation.
 */
@Configuration
public class GuardPortTestStubs {

  /**
   * Stub incident access checker.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Non-MISSING_TEAM org type → {@code incident_access_denied}
   *   <li>MISSING_TEAM + accountId {@code ...0098} → {@code team_not_assigned}
   *   <li>Otherwise → pass
   * </ul>
   */
  @Bean
  @ConditionalOnMissingBean(IncidentAccessPort.class)
  IncidentAccessPort stubIncidentAccessPort() {
    return new StubIncidentAccessPort();
  }

  /**
   * Stub police phone validator.
   *
   * <p>Rules (by last 12 bits of the UUID least-significant bits):
   *
   * <ul>
   *   <li>0x100–0x1FF → registered and assigned (pass)
   *   <li>0x200–0x2FF → NOT registered → {@code police_phone_not_registered}
   *   <li>0x300–0x3FF → registered but NOT assigned → {@code police_phone_not_assigned}
   * </ul>
   */
  @Bean
  @ConditionalOnMissingBean(PolicePhoneValidationPort.class)
  PolicePhoneValidationPort stubPolicePhoneValidationPort() {
    return new StubPolicePhoneValidationPort();
  }

  // ---------------------------------------------------------------------------
  // Static inner implementations
  // ---------------------------------------------------------------------------

  static final class StubIncidentAccessPort implements IncidentAccessPort {

    private static final long UNASSIGNED_TEAM_LEAST_BITS =
        UUID.fromString("00000000-0000-0000-0000-000000000098").getLeastSignificantBits();

    @Override
    public void checkAccess(SuriMapAuthentication auth) {
      if (auth.getOrganizationType() != OrganizationType.MISSING_TEAM) {
        throw new IncidentAccessDeniedException();
      }
      if (auth.getAccountId().getLeastSignificantBits() == UNASSIGNED_TEAM_LEAST_BITS) {
        throw new TeamNotAssignedException();
      }
    }
  }

  static final class StubPolicePhoneValidationPort implements PolicePhoneValidationPort {

    @Override
    public void checkRegistered(UUID policePhoneId) {
      int bucket = lastTwelveHexBits(policePhoneId);
      if (bucket >= 0x200 && bucket < 0x300) {
        throw new PolicePhoneNotRegisteredException();
      }
    }

    @Override
    public void checkAssigned(UUID policePhoneId) {
      int bucket = lastTwelveHexBits(policePhoneId);
      if (bucket >= 0x300 && bucket < 0x400) {
        throw new PolicePhoneNotAssignedException();
      }
    }

    private int lastTwelveHexBits(UUID id) {
      return (int) (id.getLeastSignificantBits() & 0xFFF);
    }
  }
}
