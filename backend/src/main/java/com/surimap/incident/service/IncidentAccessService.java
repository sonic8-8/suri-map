package com.surimap.incident.service;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.incident.repository.IncidentReadMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 요청한 사건에 토큰 계정의 사건 권한이 있는지 확인하는 공용 처리다. 수색 경로와 오프라인 패키지 Service에서 호출된다. */
@Service
public class IncidentAccessService {

  private final IncidentReadMapper incidentReadMapper;

  public IncidentAccessService(IncidentReadMapper incidentReadMapper) {
    this.incidentReadMapper = incidentReadMapper;
  }

  // 사건 권한의 범위는 사건 조회와 같다. 사건 권한은 배정으로 정해지므로 종료된 사건에도 같은 기준이 적용된다.
  // 사건 권한이 있을 때 반환값은 사건이 열려 있는지(OPEN)다. 쓰기 요청에서 이 값이 거짓이면 incident_closed로 거절된다.
  public boolean requireIncidentAccess(UUID incidentId, SuriMapAuthentication authentication) {
    boolean webCommand =
        authentication.getChannel() == Channel.WEB
            && authentication.getAccountType() == AccountType.COMMAND;
    boolean open;
    boolean closed;
    if (webCommand) {
      String organizationType = authentication.getOrganizationType().name();
      open =
          incidentReadMapper
              .findActiveDetailByIncidentIdAndOrganizationType(incidentId, organizationType)
              .isPresent();
      closed =
          !open
              && incidentReadMapper
                  .findTerminalDetailByIncidentIdAndOrganizationType(incidentId, organizationType)
                  .isPresent();
    } else {
      UUID accountId = UUID.fromString(authentication.getAccountId());
      open =
          incidentReadMapper.findActiveDetailByIncidentIdAndAccountId(incidentId, accountId).isPresent();
      closed =
          !open
              && incidentReadMapper
                  .findTerminalDetailByIncidentIdAndAccountId(incidentId, accountId)
                  .isPresent();
    }
    if (!open && !closed) {
      throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
    }
    return open;
  }
}
