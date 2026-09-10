package com.surimap.global.auth;

import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Bridges the shared bearer-session SecurityContext into the S5 marker/photo auth record. */
@Component
public class SecurityContextSuriMapAuthenticationResolver implements SuriMapAuthenticationResolver {

  @Override
  public SuriMapAuthentication resolve(String authorization, String channel) {
    var principal = SecurityContextHolder.getContext().getAuthentication();
    if (!(principal instanceof com.surimap.common.auth.SuriMapAuthentication authentication)
        || !authentication.isAuthenticated()) {
      throw denied();
    }

    try {
      UUID accountId = UUID.fromString(authentication.getAccountId());
      UUID policePhoneId =
          authentication.getPolicePhoneId() == null
              ? null
              : UUID.fromString(authentication.getPolicePhoneId());
      return new SuriMapAuthentication(
          accountId, authentication.getChannel().name(), policePhoneId);
    } catch (IllegalArgumentException exception) {
      throw denied();
    }
  }

  private static BusinessException denied() {
    return new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
  }
}
