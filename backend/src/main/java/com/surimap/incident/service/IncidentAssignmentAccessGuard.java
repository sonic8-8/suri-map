package com.surimap.incident.service;

import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.common.auth.guard.TeamNotAssignedException;
import com.surimap.incident.repository.IncidentReadMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * S1-1 incident_assignment 기반 incident-read guard 구현. 현재 공용 guard port는 incidentId를 받지 않으므로,
 * 여기서는 계정에 활성 배정이 하나라도 있는지만 확인하고 상세 사건별 접근은 query service에서 한 번 더 좁힌다.
 */
@Service
public class IncidentAssignmentAccessGuard implements IncidentAccessPort {

  private final IncidentReadMapper incidentReadMapper;

  public IncidentAssignmentAccessGuard(IncidentReadMapper incidentReadMapper) {
    this.incidentReadMapper = incidentReadMapper;
  }

  @Override
  public void checkAccess(SuriMapAuthentication auth) {
    if (incidentReadMapper.countActiveAssignmentsByAccountId(UUID.fromString(auth.getAccountId())) == 0) {
      throw new TeamNotAssignedException();
    }
  }
}
