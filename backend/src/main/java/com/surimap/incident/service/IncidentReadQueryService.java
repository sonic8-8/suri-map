package com.surimap.incident.service;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.IncidentAccessDeniedException;
import com.surimap.incident.repository.IncidentReadMapper;
import com.surimap.incident.repository.IncidentReadRows.AssignmentRow;
import com.surimap.incident.repository.IncidentReadRows.DetailRow;
import com.surimap.incident.repository.IncidentReadRows.ListRow;
import com.surimap.incident.repository.IncidentReadRows.MissingPersonRow;
import com.surimap.incident.repository.IncidentReadRows.TerminalDetailRow;
import com.surimap.incident.service.IncidentActiveReadResults.Assignment;
import com.surimap.incident.service.IncidentActiveReadResults.Detail;
import com.surimap.incident.service.IncidentActiveReadResults.ListItem;
import com.surimap.incident.service.IncidentActiveReadResults.ListResult;
import com.surimap.incident.service.IncidentActiveReadResults.MissingPerson;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Incident list/detail read model. 목록은 active 전용이고, 상세는 OPEN/terminal CLOSED를 구분한다. */
@Service
public class IncidentReadQueryService {

  private final IncidentReadMapper incidentReadMapper;

  public IncidentReadQueryService(IncidentReadMapper incidentReadMapper) {
    this.incidentReadMapper = incidentReadMapper;
  }

  @Transactional(readOnly = true)
  public ListResult findActiveIncidents(SuriMapAuthentication auth, String status) {
    return new ListResult(
        activeListRows(auth, status).stream()
            .map(this::toListItem)
            .toList());
  }

  @Transactional(readOnly = true)
  public Optional<Detail> findIncidentDetail(UUID incidentId, SuriMapAuthentication auth) {
    var detail =
        webCommandScope(auth)
            ? incidentReadMapper.findActiveDetailByIncidentIdAndOrganizationType(
                incidentId, auth.getOrganizationType().name())
            : incidentReadMapper.findActiveDetailByIncidentIdAndAccountId(
                incidentId, UUID.fromString(auth.getAccountId()));
    if (detail.isPresent()) {
      return detail.map(row -> toDetail(row, activeMissingPerson(row), activeAssignments(row)));
    }

    var terminalDetail =
        webCommandScope(auth)
            ? incidentReadMapper.findTerminalDetailByIncidentIdAndOrganizationType(
                incidentId, auth.getOrganizationType().name())
            : incidentReadMapper.findTerminalDetailByIncidentIdAndAccountId(
                incidentId, UUID.fromString(auth.getAccountId()));
    if (terminalDetail.isPresent()) {
      return terminalDetail.map(this::toTerminalDetail);
    }

    // 사건은 존재하지만 현재 계정 배정이 없으면 active/terminal 모두 같은 접근 거부로 처리한다.
    if (incidentReadMapper.countIncidentById(incidentId) > 0) {
      throw new IncidentAccessDeniedException();
    }
    return Optional.empty();
  }

  private java.util.List<ListRow> activeListRows(SuriMapAuthentication auth, String status) {
    if (webCommandScope(auth)) {
      return incidentReadMapper.findActiveListByOrganizationType(
          auth.getOrganizationType().name(), status);
    }
    return incidentReadMapper.findActiveListByAccountId(UUID.fromString(auth.getAccountId()), status);
  }

  private boolean webCommandScope(SuriMapAuthentication auth) {
    return auth.getChannel() == Channel.WEB && auth.getAccountType() == AccountType.COMMAND;
  }

  private ListItem toListItem(ListRow row) {
    return new ListItem(
        row.getId(),
        row.getId(),
        row.getTitle(),
        row.getStatus(),
        row.getVersion(),
        row.getClosedAt());
  }

  private Detail toDetail(
      DetailRow row,
      Optional<MissingPersonRow> missingPerson,
      java.util.List<AssignmentRow> assignments) {
    return Detail.active(
        row.getId(),
        row.getId(),
        row.getTitle(),
        row.getStatus(),
        row.getOpenedAt(),
        row.getVersion(),
        missingPerson.map(this::toMissingPerson).orElse(null),
        assignments.stream().map(this::toAssignment).toList());
  }

  private Detail toTerminalDetail(TerminalDetailRow row) {
    return Detail.terminal(
        row.getId(), row.getId(), row.getStatus(), row.getVersion(), row.getClosedAt());
  }

  private Optional<MissingPersonRow> activeMissingPerson(DetailRow row) {
    return incidentReadMapper.findMissingPersonByIncidentId(row.getId());
  }

  private java.util.List<AssignmentRow> activeAssignments(DetailRow row) {
    return incidentReadMapper.findActiveAssignmentsByIncidentId(row.getId());
  }

  private MissingPerson toMissingPerson(MissingPersonRow row) {
    return new MissingPerson(
        row.getIncidentId(),
        row.getDisplayName(),
        row.getPhotoObjectKey(),
        row.getAppearanceText(),
        row.getLastSeenLocationText(),
        row.getLastSeenAt());
  }

  private Assignment toAssignment(AssignmentRow row) {
    return new Assignment(
        row.getAccountId(),
        row.getAccountDisplayName(),
        row.getAccountType(),
        row.getOrganizationType(),
        row.getIncidentRole(),
        row.getAssignedAt());
  }
}
