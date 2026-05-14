package com.surimap.incident.service;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.external.ExternalAssignment;
import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.event.IncidentAssignmentChangedEvent;
import com.surimap.incident.event.IncidentEventPublisher;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.incident.repository.IncidentMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 112/mock polling이 감지한 incident_assignment 변경을 S1-1 write 경계에서 반영한다. */
@Service
public class IncidentAssignmentImportService {

  private static final String ASSIGNMENT_CHANGED_STATUS = "ACTIVE";

  private final IncidentMapper incidentMapper;
  private final IncidentLifecycleGuard incidentLifecycleGuard;
  private final IncidentEventPublisher incidentEventPublisher;
  private final Clock clock;

  public IncidentAssignmentImportService(
      IncidentMapper incidentMapper,
      IncidentLifecycleGuard incidentLifecycleGuard,
      IncidentEventPublisher incidentEventPublisher,
      Clock clock) {
    this.incidentMapper = incidentMapper;
    this.incidentLifecycleGuard = incidentLifecycleGuard;
    this.incidentEventPublisher = incidentEventPublisher;
    this.clock = clock;
  }

  @Transactional
  public Optional<IncidentAssignmentImportResult> importAssignmentChanges(
      String sourceIncidentId, List<ExternalAssignment> assignments) {
    if (assignments == null || assignments.isEmpty()) {
      return Optional.empty();
    }

    IncidentRecord incident = incidentMapper.findBySourceIncidentId(sourceIncidentId).orElse(null);
    if (incident == null) {
      return Optional.empty();
    }
    incidentLifecycleGuard.requireOpen(incident.getId());

    Instant now = clock.instant();
    List<String> changedAccountIds = new ArrayList<>();
    for (ExternalAssignment assignment : assignments) {
      UUID accountId = AccountIdentityCatalog.accountIdFromCodeOrUuid(assignment.accountCode());
      int inserted =
          incidentMapper.insertIncidentAssignmentIfAbsent(
              assignmentIdFor(assignment),
              incident.getId(),
              accountId,
              assignment.incidentRole(),
              assignedAt(assignment, now),
              now);
      if (inserted > 0) {
        changedAccountIds.add(accountId.toString());
      }
    }

    if (changedAccountIds.isEmpty()) {
      return Optional.empty();
    }

    incidentMapper.incrementIncidentVersion(incident.getId(), now);
    IncidentRecord updated = incidentMapper.findByIncidentId(incident.getId()).orElseThrow();
    IncidentAssignmentImportResult result =
        new IncidentAssignmentImportResult(
            updated.getId(), ASSIGNMENT_CHANGED_STATUS, updated.getVersion(), changedAccountIds);
    incidentEventPublisher.publishIncidentAssignmentChanged(
        new IncidentAssignmentChangedEvent(
            result.incidentId(), result.status(), result.version(), result.changedAccountIds()));
    return Optional.of(result);
  }

  private UUID assignmentIdFor(ExternalAssignment assignment) {
    String seed =
        assignment.externalAssignmentKey() == null
            ? assignment.accountCode()
            : assignment.externalAssignmentKey();
    return UUID.nameUUIDFromBytes(
        ("incident-assignment:" + Objects.requireNonNull(seed)).getBytes(StandardCharsets.UTF_8));
  }

  private Instant assignedAt(ExternalAssignment assignment, Instant fallback) {
    Instant assignedAt = toInstant(assignment.assignedAt());
    return assignedAt == null ? fallback : assignedAt;
  }

  private Instant toInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
