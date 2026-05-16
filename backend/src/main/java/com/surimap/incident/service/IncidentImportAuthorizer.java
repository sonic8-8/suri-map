package com.surimap.incident.service;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.incident.exception.IncidentApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 사건 가져오기 권한 검증기. {@code docs/spec/boundaries.md §4.6} Channel/Role Matrix를 따른다.
 *
 * <p>WEB 채널 + (실종팀 {@code MISSING_TEAM_COMMANDER} 또는 지구대/파출소 {@code COMMAND}+ {@code
 * FIELD_COMMANDER}) 조건을 만족해야 통과. 채널 어긋남은 {@code channel_not_allowed}, 역할 부족은 {@code role_denied} 표준
 * error code로 거부한다.
 */
@Component
class IncidentImportAuthorizer {

  void requireImportAllowed(Authentication authentication, String clientChannel, boolean internal) {
    if (internal && "INTERNAL".equals(clientChannel)) {
      return;
    }
    if (!"WEB".equals(clientChannel)) {
      throw new IncidentApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (!(authentication instanceof SuriMapAuthentication suriMapAuthentication)
        || suriMapAuthentication.getChannel() != Channel.WEB) {
      throw new IncidentApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (isMissingTeamCommander(suriMapAuthentication)
        || isPoliceSubstationFieldCommander(suriMapAuthentication)) {
      return;
    }
    throw new IncidentApiException("role_denied", HttpStatus.FORBIDDEN);
  }

  private boolean isMissingTeamCommander(SuriMapAuthentication authentication) {
    return hasAuthority(authentication, Role.MISSING_TEAM_COMMANDER);
  }

  private boolean isPoliceSubstationFieldCommander(SuriMapAuthentication authentication) {
    return authentication.getAccountType() == AccountType.COMMAND
        && authentication.getOrganizationType() == OrganizationType.POLICE_SUBSTATION
        && hasAuthority(authentication, Role.FIELD_COMMANDER);
  }

  private boolean hasAuthority(SuriMapAuthentication authentication, Role role) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> role.name().equals(authority.getAuthority()));
  }
}
