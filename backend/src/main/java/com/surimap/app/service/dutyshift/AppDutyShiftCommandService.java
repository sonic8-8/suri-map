package com.surimap.app.service.dutyshift;

import com.surimap.api.controller.dutyshift.response.DutyShiftEndResponse;
import com.surimap.api.controller.dutyshift.response.DutyShiftResponse;
import com.surimap.api.service.handover.HandoverApiException;
import com.surimap.app.controller.dutyshift.request.EndDutyShiftRequest;
import com.surimap.app.controller.dutyshift.request.StartDutyShiftRequest;
import com.surimap.dutyshift.DutyShift;
import com.surimap.dutyshift.DutyShiftMapper;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import com.surimap.summary.SearchHistorySummaryGenerationJob;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppDutyShiftCommandService {

  private static final String ACTIVE = "ACTIVE";
  private static final String ENDED = "ENDED";
  private static final String END = "END";

  private final DutyShiftMapper dutyShiftMapper;
  private final OperationalPeriodMapper operationalPeriodMapper;
  private final IncidentLifecycleGuard incidentLifecycleGuard;
  private final SearchHistorySummaryGenerationJob searchHistorySummaryGenerationJob;
  private final IdempotentResponseCache idempotentResponseCache;
  private final Map<String, IdempotencyEntry> idempotencyEntries = new LinkedHashMap<>();

  public AppDutyShiftCommandService(
      DutyShiftMapper dutyShiftMapper,
      OperationalPeriodMapper operationalPeriodMapper,
      IncidentLifecycleGuard incidentLifecycleGuard,
      SearchHistorySummaryGenerationJob searchHistorySummaryGenerationJob,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this.dutyShiftMapper = dutyShiftMapper;
    this.operationalPeriodMapper = operationalPeriodMapper;
    this.incidentLifecycleGuard = incidentLifecycleGuard;
    this.searchHistorySummaryGenerationJob = searchHistorySummaryGenerationJob;
    this.idempotentResponseCache = idempotentResponseCacheProvider.getIfAvailable();
  }

  @Transactional
  public synchronized DutyShiftResponse start(
      StartDutyShiftRequest request,
      UUID headerPolicePhoneId,
      String idempotencyKey,
      UUID actorAccountId) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("duty-start", request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        DutyShiftResponse.class,
        () -> {
          requireActor(actorAccountId);
          requirePhoneMatch(headerPolicePhoneId, request.policePhoneId());
          requireCurrentOp(request.incidentId(), request.opId());
          incidentLifecycleGuard.requireOpen(request.incidentId());
          UUID assignmentId =
              dutyShiftMapper
                  .findActiveAssignmentId(request.incidentId(), actorAccountId)
                  .orElseThrow(HandoverApiException::writeConflict);
          Instant now = Instant.now();
          DutyShift dutyShift =
              new DutyShift(
                  UUID.randomUUID(),
                  request.incidentId(),
                  request.opId(),
                  assignmentId,
                  request.policePhoneId(),
                  ACTIVE,
                  actorAccountId,
                  null,
                  now,
                  null,
                  1L,
                  now,
                  now);
          dutyShiftMapper.insert(dutyShift);
          return DutyShiftResponse.from(dutyShift);
        });
  }

  @Transactional
  public synchronized DutyShiftEndResponse end(
      UUID dutyShiftId,
      EndDutyShiftRequest request,
      UUID headerPolicePhoneId,
      String idempotencyKey,
      UUID actorAccountId) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("duty-end:" + dutyShiftId, request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        DutyShiftEndResponse.class,
        () -> {
          requireActor(actorAccountId);
          if (!END.equals(request.action())) {
            throw HandoverApiException.writeConflict();
          }
          DutyShift existing =
              dutyShiftMapper.findById(dutyShiftId).orElseThrow(HandoverApiException::writeConflict);
          requirePhoneMatch(headerPolicePhoneId, existing.getPolicePhoneId());
          if (!Objects.equals(existing.getIncidentId(), request.incidentId())
              || !Objects.equals(existing.getOpId(), request.opId())) {
            throw HandoverApiException.writeConflict();
          }
          requireCurrentOp(request.incidentId(), request.opId());
          incidentLifecycleGuard.requireOpen(request.incidentId());
          Instant endedAt = Instant.now();
          long nextVersion = existing.getVersion() + 1;
          dutyShiftMapper.end(dutyShiftId, actorAccountId, endedAt, nextVersion);
          DutyShift ended =
              new DutyShift(
                  existing.getId(),
                  existing.getIncidentId(),
                  existing.getOpId(),
                  existing.getIncidentAssignmentId(),
                  existing.getPolicePhoneId(),
                  ENDED,
                  existing.getStartedByAccountId(),
                  actorAccountId,
                  existing.getStartedAt(),
                  endedAt,
                  nextVersion,
                  existing.getCreatedAt(),
                  endedAt);
          searchHistorySummaryGenerationJob.enqueueForDutyShiftEnd(ended, actorAccountId);
          return DutyShiftEndResponse.from(ended);
        });
  }

  private void requireCurrentOp(UUID incidentId, UUID opId) {
    OperationalPeriod current =
        operationalPeriodMapper
            .findActiveByIncident(incidentId)
            .orElseThrow(HandoverApiException::opRequired);
    if (!current.getId().equals(opId)) {
      throw HandoverApiException.opMismatch();
    }
  }

  private void requireActor(UUID actorAccountId) {
    if (actorAccountId == null) {
      throw HandoverApiException.writeConflict();
    }
  }

  private void requirePhoneMatch(UUID headerPolicePhoneId, UUID requestPolicePhoneId) {
    if (headerPolicePhoneId == null || !headerPolicePhoneId.equals(requestPolicePhoneId)) {
      throw HandoverApiException.writeConflict();
    }
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw HandoverApiException.writeConflict();
    }
  }

  private <T> T replayOrRun(
      String idempotencyKey, String fingerprint, Class<T> responseType, Operation<T> operation) {
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          endpointFor(responseType),
          idempotencyKey,
          fingerprint,
          responseType.equals(DutyShiftResponse.class) ? 201 : 200,
          responseType,
          operation::run,
          this::metadataFor);
    }
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing != null) {
      if (!existing.fingerprint().equals(fingerprint)) {
        throw HandoverApiException.idempotencyMismatch();
      }
      if (!responseType.isInstance(existing.response())) {
        throw HandoverApiException.writeConflict();
      }
      return responseType.cast(existing.response());
    }
    T response = operation.run();
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

  private record IdempotencyEntry(String fingerprint, Object response) {}

  private static String endpointFor(Class<?> responseType) {
    if (responseType.equals(DutyShiftResponse.class)) {
      return "POST /api/duty-shifts";
    }
    return "PATCH /api/duty-shifts/{dutyShiftId}";
  }

  private <T> ResponseMetadata metadataFor(T response) {
    if (response instanceof DutyShiftResponse dutyShift) {
      return new ResponseMetadata(
          dutyShift.id().toString(), dutyShift.status(), dutyShift.version(), dutyShift.version());
    }
    if (response instanceof DutyShiftEndResponse ended) {
      return new ResponseMetadata(
          ended.id().toString(), ended.status(), ended.version(), ended.version());
    }
    throw HandoverApiException.writeConflict();
  }

  @FunctionalInterface
  private interface Operation<T> {
    T run();
  }
}
