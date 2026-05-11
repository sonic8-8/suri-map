package com.surimap.api.service.handover;

import com.surimap.api.controller.handover.request.CreateHandoverMemoRequest;
import com.surimap.api.controller.handover.response.HandoverMemoListItemResponse;
import com.surimap.api.controller.handover.response.HandoverMemoListResponse;
import com.surimap.api.controller.handover.response.HandoverMemoResponse;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.handover.HandoverMemo;
import com.surimap.handover.HandoverMemoMapper;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoverMemoApiService {

  private static final String OPERATIONAL_PERIOD = "OPERATIONAL_PERIOD";
  private static final String DUTY_SHIFT = "DUTY_SHIFT";
  private static final String SEARCH_PATH = "SEARCH_PATH";
  private static final String SEARCH_AREA = "SEARCH_AREA";
  private static final String MARKER = "MARKER";
  private static final String ACTIVE = "ACTIVE";
  private static final int PAYLOAD_FORMAT_VERSION = 1;

  private final HandoverMemoMapper handoverMemoMapper;
  private final OperationalPeriodMapper operationalPeriodMapper;
  private final IncidentLifecycleGuard incidentLifecycleGuard;
  private final EventHub eventHub;
  private final Map<String, IdempotencyEntry> idempotencyEntries = new LinkedHashMap<>();

  public HandoverMemoApiService(
      HandoverMemoMapper handoverMemoMapper,
      OperationalPeriodMapper operationalPeriodMapper,
      IncidentLifecycleGuard incidentLifecycleGuard,
      EventHub eventHub) {
    this.handoverMemoMapper = handoverMemoMapper;
    this.operationalPeriodMapper = operationalPeriodMapper;
    this.incidentLifecycleGuard = incidentLifecycleGuard;
    this.eventHub = eventHub;
  }

  @Transactional
  public synchronized HandoverMemoResponse create(
      CreateHandoverMemoRequest request, String idempotencyKey, UUID actorAccountId) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("handover-memo-create", request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        HandoverMemoResponse.class,
        () -> {
          requireActor(actorAccountId);
          requireCurrentOp(request.incidentId(), request.opId());
          incidentLifecycleGuard.requireOpen(request.incidentId());
          String targetType = requireTargetType(request.memoTargetType());
          UUID targetId = targetId(targetType, request.opId(), request.memoTargetId());
          UUID dutyShiftId = DUTY_SHIFT.equals(targetType) ? targetId : null;
          Instant now = Instant.now();
          HandoverMemo memo =
              new HandoverMemo(
                  UUID.randomUUID(),
                  request.opId(),
                  targetType,
                  targetId,
                  request.content(),
                  actorAccountId,
                  dutyShiftId,
                  ACTIVE,
                  1L,
                  now,
                  now);
          handoverMemoMapper.insert(memo);
          eventHub.publish(publishRequest(request.incidentId(), memo, now));
          return HandoverMemoResponse.from(memo);
        });
  }

  public HandoverMemoListResponse list(
      UUID incidentId, UUID opId, String memoTargetType, UUID memoTargetId) {
    String targetType = memoTargetType == null ? null : requireTargetType(memoTargetType);
    return new HandoverMemoListResponse(
        handoverMemoMapper.findByContext(incidentId, opId, targetType, memoTargetId).stream()
            .map(HandoverMemoListItemResponse::from)
            .toList());
  }

  private PublishRequest publishRequest(UUID incidentId, HandoverMemo memo, Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", memo.getId().toString());
    payload.put("incidentId", incidentId.toString());
    payload.put("opId", memo.getOpId().toString());
    payload.put("status", memo.getStatus());
    payload.put("version", memo.getVersion());
    payload.put("targetType", memo.getMemoTargetType());
    payload.put("targetId", memo.getMemoTargetId().toString());
    return new PublishRequest(
        eventIdFor(memo),
        incidentId,
        "HANDOVER_MEMO_CREATED",
        PAYLOAD_FORMAT_VERSION,
        "handover_memo",
        memo.getId(),
        occurredAt,
        payload);
  }

  private UUID eventIdFor(HandoverMemo memo) {
    String seed = "event:HANDOVER_MEMO_CREATED:" + memo.getId() + ":v" + memo.getVersion();
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
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

  private String requireTargetType(String targetType) {
    if (OPERATIONAL_PERIOD.equals(targetType)
        || DUTY_SHIFT.equals(targetType)
        || SEARCH_PATH.equals(targetType)
        || SEARCH_AREA.equals(targetType)
        || MARKER.equals(targetType)) {
      return targetType;
    }
    throw HandoverApiException.writeConflict();
  }

  private UUID targetId(String targetType, UUID opId, UUID requestedTargetId) {
    if (requestedTargetId != null) {
      return requestedTargetId;
    }
    if (OPERATIONAL_PERIOD.equals(targetType)) {
      return opId;
    }
    throw HandoverApiException.writeConflict();
  }

  private void requireActor(UUID actorAccountId) {
    if (actorAccountId == null) {
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

  @FunctionalInterface
  private interface Operation<T> {
    T run();
  }
}
