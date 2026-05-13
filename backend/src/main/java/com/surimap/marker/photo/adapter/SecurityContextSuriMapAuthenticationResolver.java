package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.security.SuriMapAuthenticationResolver;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

  private static PhotoApiException denied() {
    return new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }
}
