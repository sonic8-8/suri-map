package com.surimap.api.service.operationalperiod;

import com.surimap.api.controller.operationalperiod.request.CreateOperationalPeriodRequest;
import com.surimap.api.controller.operationalperiod.response.OperationalPeriodListResponse;
import com.surimap.api.controller.operationalperiod.response.OperationalPeriodResponse;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.handover.HandoverMemo;
import com.surimap.handover.HandoverMemoMapper;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import com.surimap.operationalperiod.event.EventPublisherPort;
import com.surimap.operationalperiod.event.OpTransitionedPublishRequest;
import com.surimap.summary.SearchHistorySummaryGenerationJob;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalPeriodApiService {

  private static final String ACTIVE = "ACTIVE";
  private static final String RE_SEARCH = "RE_SEARCH";
  private static final String AREA_CHANGED = "AREA_CHANGED";
  private static final String OTHER = "OTHER";
  private static final String OPERATIONAL_PERIOD = "OPERATIONAL_PERIOD";
  private static final int PAYLOAD_FORMAT_VERSION = 1;

  private final OperationalPeriodMapper mapper;
  private final HandoverMemoMapper handoverMemoMapper;
  private final EventPublisherPort eventPublisher;
  private final EventHub eventHub;
  private final IncidentLifecycleGuard incidentLifecycleGuard;
  private final SearchHistorySummaryGenerationJob searchHistorySummaryGenerationJob;
  private final IdempotentResponseCache idempotentResponseCache;
  private final Map<String, IdempotencyEntry> idempotencyEntries = new LinkedHashMap<>();

  public OperationalPeriodApiService(
      OperationalPeriodMapper mapper,
      HandoverMemoMapper handoverMemoMapper,
      EventPublisherPort eventPublisher,
      EventHub eventHub,
      IncidentLifecycleGuard incidentLifecycleGuard,
      SearchHistorySummaryGenerationJob searchHistorySummaryGenerationJob,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this.mapper = mapper;
    this.handoverMemoMapper = handoverMemoMapper;
    this.eventPublisher = eventPublisher;
    this.eventHub = eventHub;
    this.incidentLifecycleGuard = incidentLifecycleGuard;
    this.searchHistorySummaryGenerationJob = searchHistorySummaryGenerationJob;
    this.idempotentResponseCache = idempotentResponseCacheProvider.getIfAvailable();
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
          createTransitionHandoverMemoIfPresent(request, previous, actorAccountId, now);
          searchHistorySummaryGenerationJob.enqueueForOperationalPeriodTransition(
              previous, created, actorAccountId);

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
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          "POST /api/operational-periods",
          idempotencyKey,
          fingerprint,
          201,
          OperationalPeriodResponse.class,
          operation::run,
          response ->
              new ResponseMetadata(
                  response.id().toString(),
                  response.status(),
                  response.version(),
                  response.sequenceNumber()));
    }
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

  private void createTransitionHandoverMemoIfPresent(
      CreateOperationalPeriodRequest request,
      OperationalPeriod previous,
      UUID actorAccountId,
      Instant occurredAt) {
    if (request.handoverMemo() == null || request.handoverMemo().isBlank()) {
      return;
    }
    HandoverMemo memo =
        new HandoverMemo(
            UUID.randomUUID(),
            previous.getId(),
            OPERATIONAL_PERIOD,
            previous.getId(),
            request.handoverMemo(),
            actorAccountId,
            null,
            ACTIVE,
            1L,
            occurredAt,
            occurredAt);
    handoverMemoMapper.insert(memo);
    eventHub.publish(handoverMemoPublishRequest(previous.getIncidentId(), memo, occurredAt));
  }

  private PublishRequest handoverMemoPublishRequest(
      UUID incidentId, HandoverMemo memo, Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", memo.getId().toString());
    payload.put("incidentId", incidentId.toString());
    payload.put("opId", memo.getOpId().toString());
    payload.put("status", memo.getStatus());
    payload.put("version", memo.getVersion());
    payload.put("targetType", memo.getMemoTargetType());
    payload.put("targetId", memo.getMemoTargetId().toString());
    return new PublishRequest(
        handoverMemoEventIdFor(memo),
        incidentId,
        "HANDOVER_MEMO_CREATED",
        PAYLOAD_FORMAT_VERSION,
        "handover_memo",
        memo.getId(),
        occurredAt,
        payload);
  }

  private UUID handoverMemoEventIdFor(HandoverMemo memo) {
    String seed = "event:HANDOVER_MEMO_CREATED:" + memo.getId() + ":v" + memo.getVersion();
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private record IdempotencyEntry(String fingerprint, OperationalPeriodResponse response) {}

  @FunctionalInterface
  private interface Operation {
    OperationalPeriodResponse run();
  }
}
