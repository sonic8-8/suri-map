package com.surimap.incident.controller.response;

import com.surimap.incident.service.IncidentActiveReadResults.Detail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GET /api/incidents/{incidentId} 상세 응답. OPEN과 CLOSED의 JSON shape를 타입으로 분리한다. */
public sealed interface IncidentDetailResponse
    permits IncidentDetailResponse.Active, IncidentDetailResponse.Terminal {

  static IncidentDetailResponse from(Detail detail) {
    if (detail instanceof Detail.Active active) {
      return Active.from(active);
    }
    if (detail instanceof Detail.Terminal terminal) {
      return Terminal.from(terminal);
    }
    throw new IllegalArgumentException("Unsupported incident detail type: " + detail.getClass());
  }

  /** OPEN 상세 응답. missingPerson이 없더라도 키는 null로 유지해 active 계약을 지킨다. */
  record Active(
      UUID id,
      UUID incidentId,
      String title,
      String status,
      Instant openedAt,
      long version,
      IncidentDetailMissingPersonSummaryResponse missingPerson,
      List<IncidentAssignmentResponse> assignments)
      implements IncidentDetailResponse {
    public Active {
      assignments = List.copyOf(assignments);
    }

    static Active from(Detail.Active detail) {
      return new Active(
          detail.id(),
          detail.incidentId(),
          detail.title(),
          detail.status(),
          detail.openedAt(),
          detail.version(),
          IncidentDetailMissingPersonSummaryResponse.from(detail.missingPerson()),
          detail.assignments().stream().map(IncidentAssignmentResponse::from).toList());
    }
  }

  /** CLOSED 상세 응답. 실종자 PII와 active assignment 목록을 구조적으로 포함하지 않는다. */
  record Terminal(
      UUID id,
      UUID incidentId,
      String status,
      long version,
      Instant closedAt,
      IncidentTerminalSnapshotResponse terminalSnapshot,
      String writeDisabledReason)
      implements IncidentDetailResponse {
    static Terminal from(Detail.Terminal detail) {
      return new Terminal(
          detail.id(),
          detail.incidentId(),
          detail.status(),
          detail.version(),
          detail.closedAt(),
          IncidentTerminalSnapshotResponse.from(detail.terminalSnapshot()),
          detail.writeDisabledReason());
    }
  }
}
