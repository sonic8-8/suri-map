package com.surimap.incident.service;

import com.surimap.common.auth.guard.IncidentAccessDeniedException;
import com.surimap.incident.repository.IncidentReadMapper;
import com.surimap.incident.repository.IncidentReadRows.AssignmentRow;
import com.surimap.incident.repository.IncidentReadRows.DetailRow;
import com.surimap.incident.repository.IncidentReadRows.ListRow;
import com.surimap.incident.repository.IncidentReadRows.MissingPersonRow;
import com.surimap.incident.service.IncidentActiveReadResults.Assignment;
import com.surimap.incident.service.IncidentActiveReadResults.Detail;
import com.surimap.incident.service.IncidentActiveReadResults.ListItem;
import com.surimap.incident.service.IncidentActiveReadResults.ListResult;
import com.surimap.incident.service.IncidentActiveReadResults.MissingPerson;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Active incident list/detail read model. CLOSED terminal·purge 상태 조립은 후속 task 범위다. */
@Service
public class IncidentReadQueryService {

  private final IncidentReadMapper incidentReadMapper;

  public IncidentReadQueryService(IncidentReadMapper incidentReadMapper) {
    this.incidentReadMapper = incidentReadMapper;
  }

  @Transactional(readOnly = true)
  public ListResult findActiveIncidents(String accountId, String status) {
    return new ListResult(
        incidentReadMapper.findActiveListByAccountId(accountId, status).stream()
            .map(this::toListItem)
            .toList());
  }

  @Transactional(readOnly = true)
  public Optional<Detail> findActiveIncidentDetail(UUID incidentId, String accountId) {
    var detail = incidentReadMapper.findActiveDetailByIncidentIdAndAccountId(incidentId, accountId);
    if (detail.isPresent()) {
      return detail.map(row -> toDetail(row, activeMissingPerson(row), activeAssignments(row)));
    }
    // 사건은 active지만 현재 계정 배정이 없으면 S1-1 detail 계약대로 403으로 거부한다.
    if (incidentReadMapper.countActiveIncidentById(incidentId) > 0) {
      throw new IncidentAccessDeniedException();
    }
    return Optional.empty();
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
    return new Detail(
        row.getId(),
        row.getId(),
        row.getStatus(),
        row.getVersion(),
        missingPerson.map(this::toMissingPerson).orElse(null),
        assignments.stream().map(this::toAssignment).toList());
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
    return new Assignment(row.getAccountId(), row.getIncidentRole());
  }
}
