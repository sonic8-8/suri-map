package com.surimap.api.service.operationalperiod;

import com.surimap.api.controller.operationalperiod.request.CreateOperationalPeriodRequest;
import com.surimap.api.controller.operationalperiod.response.OperationalPeriodListResponse;
import com.surimap.api.controller.operationalperiod.response.OperationalPeriodResponse;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import com.surimap.operationalperiod.event.EventPublisherPort;
import com.surimap.operationalperiod.event.OpTransitionedPublishRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalPeriodApiService {

  private static final String ACTIVE = "ACTIVE";
  private static final String RE_SEARCH = "RE_SEARCH";
  private static final String AREA_CHANGED = "AREA_CHANGED";
  private static final String OTHER = "OTHER";

  private final OperationalPeriodMapper mapper;
  private final EventPublisherPort eventPublisher;
  private final IncidentLifecycleGuard incidentLifecycleGuard;
  private final Map<String, IdempotencyEntry> idempotencyEntries = new LinkedHashMap<>();

  public OperationalPeriodApiService(
      OperationalPeriodMapper mapper,
      EventPublisherPort eventPublisher,
      IncidentLifecycleGuard incidentLifecycleGuard) {
    this.mapper = mapper;
    this.eventPublisher = eventPublisher;
    this.incidentLifecycleGuard = incidentLifecycleGuard;
  }

  @Transactional
  public synchronized OperationalPeriodResponse create(
      CreateOperationalPeriodRequest request, String idempotencyKey, UUID actorAccountId) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("create", request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        () -> {
          requireManualReason(request.reason(), request.reasonMemo());
          incidentLifecycleGuard.requireOpen(request.incidentId());

          OperationalPeriod previous =
              mapper
                  .findActiveByIncident(request.incidentId())
                  .orElseThrow(OperationalPeriodApiException::writeConflict);
          List<OperationalPeriod> periods =
              mapper.findAllByIncidentOrderBySequence(request.incidentId());
          int nextSequence =
              periods.stream()
                      .mapToInt(OperationalPeriod::getSequenceNumber)
                      .max()
                      .orElse(previous.getSequenceNumber())
                  + 1;

          Instant now = Instant.now();
          mapper.endActive(previous.getId(), actorAccountId, now, previous.getVersion() + 1);

          OperationalPeriod created =
              new OperationalPeriod(
                  UUID.randomUUID(),
                  request.incidentId(),
                  nextSequence,
                  ACTIVE,
                  request.reason(),
                  request.reasonMemo(),
                  actorAccountId,
                  null,
                  now,
                  null,
                  1L,
                  now,
                  now);
          mapper.insert(created);
          eventPublisher.publish(
              new OpTransitionedPublishRequest(
                  "OP_TRANSITIONED",
                  created.getId(),
                  created.getIncidentId(),
                  created.getId(),
                  created.getStatus(),
                  created.getVersion(),
                  created.getSequenceNumber(),
                  previous.getId(),
                  created.getId()));

          return OperationalPeriodResponse.from(created);
        });
  }

  public OperationalPeriodListResponse list(UUID incidentId) {
    UUID currentOpId = mapper.findActiveByIncident(incidentId).map(OperationalPeriod::getId).orElse(null);
    return OperationalPeriodListResponse.from(
        currentOpId, mapper.findAllByIncidentOrderBySequence(incidentId).stream().map(this::toRow).toList());
  }

  private com.surimap.operationalperiod.query.OperationalPeriodRow toRow(OperationalPeriod op) {
    return new com.surimap.operationalperiod.query.OperationalPeriodRow(
        op.getId(),
        op.getIncidentId(),
        op.getStatus(),
        op.getSequenceNumber(),
        op.getStartedAt(),
        op.getEndedAt(),
        op.getReason(),
        op.getVersion());
  }

  private void requireManualReason(String reason, String reasonMemo) {
    if (!RE_SEARCH.equals(reason) && !AREA_CHANGED.equals(reason) && !OTHER.equals(reason)) {
      throw OperationalPeriodApiException.writeConflict();
    }
    if (OTHER.equals(reason) && (reasonMemo == null || reasonMemo.isBlank())) {
      throw OperationalPeriodApiException.writeConflict();
    }
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw OperationalPeriodApiException.writeConflict();
    }
  }

  private OperationalPeriodResponse replayOrRun(
      String idempotencyKey, String fingerprint, Operation operation) {
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing != null) {
      if (!existing.fingerprint().equals(fingerprint)) {
        throw OperationalPeriodApiException.idempotencyMismatch();
      }
      return existing.response();
    }
    OperationalPeriodResponse response = operation.run();
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(fingerprint, response));
    return response;
  }

  private String fingerprint(String operation, Object request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed =
          digest.digest(
              (operation + ":" + String.valueOf(request)).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private record IdempotencyEntry(String fingerprint, OperationalPeriodResponse response) {}

  @FunctionalInterface
  private interface Operation {
    OperationalPeriodResponse run();
  }
}
