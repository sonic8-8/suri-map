package com.surimap.incident.service;

import com.surimap.external.ExternalIncident;
import com.surimap.external.ExternalIncidentAdapter;
import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.domain.Mock112WebhookEventRecord;
import com.surimap.incident.exception.IncidentApiException;
import com.surimap.incident.exception.IncidentImportDependencyException;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.incident.repository.Mock112WebhookEventMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Mock112WebhookService {

  private static final String COMPLETED = "COMPLETED";

  private final Mock112WebhookEventMapper webhookEventMapper;
  private final IncidentImportService incidentImportService;
  private final IncidentAssignmentImportService assignmentImportService;
  private final ExternalIncidentAdapter externalIncidentAdapter;
  private final IncidentMapper incidentMapper;
  private final Clock clock;

  public Mock112WebhookService(
      Mock112WebhookEventMapper webhookEventMapper,
      IncidentImportService incidentImportService,
      IncidentAssignmentImportService assignmentImportService,
      ExternalIncidentAdapter externalIncidentAdapter,
      IncidentMapper incidentMapper,
      Clock clock) {
    this.webhookEventMapper = webhookEventMapper;
    this.incidentImportService = incidentImportService;
    this.assignmentImportService = assignmentImportService;
    this.externalIncidentAdapter = externalIncidentAdapter;
    this.incidentMapper = incidentMapper;
    this.clock = clock;
  }

  @Transactional
  public Mock112WebhookResult handle(Mock112WebhookCommand command) {
    requireValidCommand(command);
    Mock112WebhookEventType eventType = Mock112WebhookEventType.parse(command.eventType());
    var existing = webhookEventMapper.findByEventId(command.eventId());
    if (existing.isPresent()) {
      return replay(existing.get(), command.requestBodyHash());
    }

    Instant now = clock.instant();
    webhookEventMapper.insertReserved(
        command.eventId(),
        eventType.name(),
        command.sourceIncidentId(),
        command.requestBodyHash(),
        now);

    Mock112WebhookResult result =
        switch (eventType) {
          case INCIDENT_READY -> importReadyIncident(command);
          case INCIDENT_ASSIGNMENT_CHANGED -> importAssignmentChanges(command);
        };
    webhookEventMapper.complete(
        command.eventId(),
        result.incidentId(),
        result.status(),
        result.version(),
        responseBodyJson(result),
        now);
    return result;
  }

  private Mock112WebhookResult importReadyIncident(Mock112WebhookCommand command) {
    IncidentImportResult result =
        incidentImportService.importIncident(
            IncidentImportCommand.internal(
                command.sourceIncidentId(), "mock112-webhook:" + command.eventId()));
    try {
      externalIncidentAdapter.markImported(command.sourceIncidentId().toString());
    } catch (RuntimeException exception) {
      throw new IncidentImportDependencyException(exception);
    }
    return new Mock112WebhookResult(
        command.eventId(),
        command.eventType(),
        command.sourceIncidentId(),
        result.incidentId(),
        result.status(),
        result.version());
  }

  private Mock112WebhookResult importAssignmentChanges(Mock112WebhookCommand command) {
    ExternalIncident externalIncident = fetchExternalIncident(command.sourceIncidentId());
    var assignmentResult =
        assignmentImportService.importAssignmentChanges(
            command.sourceIncidentId().toString(), externalIncident.assignments());
    if (assignmentResult.isPresent()) {
      IncidentAssignmentImportResult result = assignmentResult.get();
      return new Mock112WebhookResult(
          command.eventId(),
          command.eventType(),
          command.sourceIncidentId(),
          result.incidentId(),
          result.status(),
          result.version());
    }

    IncidentRecord existing =
        incidentMapper
            .findBySourceIncidentId(command.sourceIncidentId())
            .orElseThrow(
                () -> new IncidentApiException("mock112_event_invalid", HttpStatus.CONFLICT));
    return new Mock112WebhookResult(
        command.eventId(),
        command.eventType(),
        command.sourceIncidentId(),
        existing.getId(),
        existing.getStatus(),
        existing.getVersion());
  }

  private Mock112WebhookResult replay(
      Mock112WebhookEventRecord existing, String requestBodyHash) {
    if (!Objects.equals(existing.getRequestBodyHash(), requestBodyHash)) {
      throw new IncidentApiException("idempotency_mismatch", HttpStatus.CONFLICT);
    }
    if (!COMPLETED.equals(existing.getWebhookStatus()) || existing.getIncidentId() == null) {
      throw new IncidentApiException("write_conflict", HttpStatus.CONFLICT);
    }
    return new Mock112WebhookResult(
        existing.getEventId(),
        existing.getEventType(),
        existing.getSourceIncidentId(),
        existing.getIncidentId(),
        existing.getIncidentStatus(),
        existing.getIncidentVersion());
  }

  private ExternalIncident fetchExternalIncident(UUID sourceIncidentId) {
    try {
      return Objects.requireNonNull(externalIncidentAdapter.fetchIncident(sourceIncidentId.toString()));
    } catch (RuntimeException exception) {
      throw new IncidentImportDependencyException(exception);
    }
  }

  private void requireValidCommand(Mock112WebhookCommand command) {
    if (command.eventId() == null
        || command.eventId().isBlank()
        || command.eventType() == null
        || command.eventType().isBlank()
        || command.sourceIncidentId() == null
        || command.requestBodyHash() == null
        || command.requestBodyHash().isBlank()) {
      throw new IncidentApiException("mock112_event_invalid", HttpStatus.CONFLICT);
    }
  }

  private String responseBodyJson(Mock112WebhookResult result) {
    return "{\"eventId\":\""
        + result.eventId()
        + "\",\"eventType\":\""
        + result.eventType()
        + "\",\"sourceIncidentId\":\""
        + result.sourceIncidentId()
        + "\",\"incidentId\":\""
        + result.incidentId()
        + "\",\"status\":\""
        + result.status()
        + "\",\"version\":"
        + result.version()
        + "}";
  }
}
