package com.surimap.api.service.opcomparison;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.api.controller.opcomparison.request.CreateOpComparisonRequest;
import com.surimap.api.controller.opcomparison.response.OpComparisonResponse;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.opcomparison.OpComparisonAnalysisMapper;
import com.surimap.opcomparison.OpComparisonAnalysisRecord;
import com.surimap.opcomparison.OpComparisonAnalysisStatus;
import com.surimap.opcomparison.OpComparisonEvidenceBuilder;
import com.surimap.opcomparison.OpComparisonEvidencePackage;
import com.surimap.opcomparison.OpComparisonMetricsCalculator;
import com.surimap.opcomparison.OpComparisonNarrativePort;
import com.surimap.opcomparison.OpComparisonNarrativeRequest;
import com.surimap.opcomparison.OpComparisonNarrativeResult;
import com.surimap.opcomparison.OpComparisonNarrativeStatus;
import com.surimap.opcomparison.OpComparisonRegionFactMapper;
import com.surimap.opcomparison.OpComparisonSourceCollector;
import com.surimap.opcomparison.OpComparisonSourceSnapshot;
import com.surimap.opcomparison.OpComparisonThresholdFilter;
import com.surimap.opcomparison.OpComparisonThresholdResult;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpComparisonApiService {

  static final String EVENT_TYPE = "OP_COMPARISON_ANALYSIS_CHANGED";
  private static final String ENDPOINT = "POST /api/operational-periods/comparisons";
  private static final String SOURCE_ENTITY_TYPE = "op_comparison_analysis";
  private static final int PAYLOAD_FORMAT_VERSION = 1;
  private static final BigDecimal REGION_BUFFER_METERS = BigDecimal.valueOf(15);
  private static final BigDecimal MINIMUM_REGION_AREA_SQUARE_METERS = BigDecimal.valueOf(50);

  private final OpComparisonAnalysisMapper analysisMapper;
  private final OpComparisonSourceCollector sourceCollector;
  private final OpComparisonRegionFactMapper regionFactMapper;
  private final OpComparisonNarrativePort narrativePort;
  private final EventHub eventHub;
  private final IncidentLifecycleGuard incidentLifecycleGuard;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final OpComparisonMetricsCalculator metricsCalculator = new OpComparisonMetricsCalculator();
  private final OpComparisonThresholdFilter thresholdFilter = new OpComparisonThresholdFilter();
  private final OpComparisonEvidenceBuilder evidenceBuilder = new OpComparisonEvidenceBuilder();
  private final IdempotentResponseCache idempotentResponseCache;
  private final Map<String, IdempotencyEntry> idempotencyEntries = new LinkedHashMap<>();

  public OpComparisonApiService(
      OpComparisonAnalysisMapper analysisMapper,
      OpComparisonSourceCollector sourceCollector,
      OpComparisonRegionFactMapper regionFactMapper,
      OpComparisonNarrativePort narrativePort,
      EventHub eventHub,
      IncidentLifecycleGuard incidentLifecycleGuard,
      ObjectMapper objectMapper,
      Clock clock,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this.analysisMapper = analysisMapper;
    this.sourceCollector = sourceCollector;
    this.regionFactMapper = regionFactMapper;
    this.narrativePort = narrativePort;
    this.eventHub = eventHub;
    this.incidentLifecycleGuard = incidentLifecycleGuard;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.idempotentResponseCache = idempotentResponseCacheProvider.getIfAvailable();
  }

  @Transactional
  public synchronized OpComparisonResponse create(
      CreateOpComparisonRequest request, String idempotencyKey, UUID actorAccountId) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint(request);
    return replayOrRun(idempotencyKey, fingerprint, () -> createOnce(request, actorAccountId));
  }

  private OpComparisonResponse createOnce(CreateOpComparisonRequest request, UUID actorAccountId) {
    if (actorAccountId == null) {
      throw OpComparisonApiException.writeConflict();
    }
    incidentLifecycleGuard.requireOpen(request.incidentId());

    OpComparisonSourceSnapshot snapshot =
        sourceCollector.collect(request.incidentId(), request.operationalPeriodIds());
    String requestHash =
        requestHash(snapshot.incidentId(), snapshot.operationalPeriodIds(), snapshot.sourceDataHash());
    var existing = analysisMapper.findByRequestHash(requestHash);
    if (existing.isPresent()) {
      return OpComparisonResponse.from(existing.get(), objectMapper);
    }

    OpComparisonAnalysisRecord initial = initialRecord(snapshot, requestHash, actorAccountId);
    try {
      analysisMapper.insert(initial);
    } catch (DuplicateKeyException ignored) {
      return OpComparisonResponse.from(
          analysisMapper.findByRequestHash(requestHash).orElseThrow(OpComparisonApiException::writeConflict),
          objectMapper);
    }

    OpComparisonAnalysisRecord finalRecord = generate(initial, snapshot);
    publishChanged(finalRecord);
    return OpComparisonResponse.from(finalRecord, objectMapper);
  }

  private OpComparisonAnalysisRecord generate(
      OpComparisonAnalysisRecord initial, OpComparisonSourceSnapshot snapshot) {
    var metrics = metricsCalculator.calculate(snapshot.metricsSources());
    var regionFacts =
        regionFactMapper.findRegionFacts(
            snapshot.operationalPeriodIds(),
            REGION_BUFFER_METERS,
            MINIMUM_REGION_AREA_SQUARE_METERS);
    OpComparisonThresholdResult threshold = thresholdFilter.filter(metrics, regionFacts);

    Instant deterministicAt = clock.instant();
    String metricsJson = toJson(metrics);
    String diffFactsJson = toJson(threshold.diffFacts());
    String regionFactsJson = toJson(threshold.regionFacts());
    analysisMapper.markDeterministicReady(
        initial.id(),
        metricsJson,
        diffFactsJson,
        regionFactsJson,
        threshold.narrativeStatus(),
        null,
        deterministicAt,
        deterministicAt);
    OpComparisonAnalysisRecord deterministic =
        withDeterministicResult(
            initial, metricsJson, diffFactsJson, regionFactsJson, threshold.narrativeStatus(), deterministicAt);

    if (threshold.narrativeStatus() != OpComparisonNarrativeStatus.GENERATING) {
      return deterministic;
    }

    OpComparisonEvidencePackage evidence =
        evidenceBuilder.build(initial.id(), initial.incidentId(), metrics, threshold);
    OpComparisonNarrativeResult result = generateNarrative(evidence);
    Instant narrativeAt = clock.instant();
    OpComparisonNarrativeStatus narrativeStatus = result.status();
    String observationsJson = result.observationsJson();
    String failureReason =
        narrativeStatus == OpComparisonNarrativeStatus.READY ? null : result.failureReason();
    analysisMapper.updateNarrativeResult(
        initial.id(),
        OpComparisonAnalysisStatus.READY,
        narrativeStatus,
        observationsJson,
        failureReason,
        narrativeAt,
        narrativeAt);
    return withNarrativeResult(deterministic, narrativeStatus, observationsJson, failureReason, narrativeAt);
  }

  private OpComparisonNarrativeResult generateNarrative(OpComparisonEvidencePackage evidence) {
    try {
      OpComparisonNarrativeResult result =
          narrativePort.generate(new OpComparisonNarrativeRequest(evidence));
      return result == null
          ? OpComparisonNarrativeResult.failed(OpComparisonNarrativeResult.PROVIDER_FAILURE)
          : result;
    } catch (RuntimeException exception) {
      return OpComparisonNarrativeResult.failed(OpComparisonNarrativeResult.PROVIDER_FAILURE);
    }
  }

  private OpComparisonAnalysisRecord initialRecord(
      OpComparisonSourceSnapshot snapshot, String requestHash, UUID actorAccountId) {
    Instant now = clock.instant();
    return new OpComparisonAnalysisRecord(
        UUID.randomUUID(),
        snapshot.incidentId(),
        toJson(snapshot.operationalPeriodIds().stream().map(UUID::toString).toList()),
        requestHash,
        snapshot.sourceDataHash(),
        OpComparisonAnalysisStatus.GENERATING,
        "[]",
        "[]",
        "[]",
        OpComparisonNarrativeStatus.GENERATING,
        null,
        null,
        actorAccountId,
        now,
        null,
        1L,
        now,
        now);
  }

  private OpComparisonAnalysisRecord withDeterministicResult(
      OpComparisonAnalysisRecord record,
      String metricsJson,
      String diffFactsJson,
      String regionFactsJson,
      OpComparisonNarrativeStatus narrativeStatus,
      Instant generatedAt) {
    return new OpComparisonAnalysisRecord(
        record.id(),
        record.incidentId(),
        record.operationalPeriodIdsJson(),
        record.requestHash(),
        record.sourceDataHash(),
        OpComparisonAnalysisStatus.READY,
        metricsJson,
        diffFactsJson,
        regionFactsJson,
        narrativeStatus,
        record.observationsJson(),
        record.failureReason(),
        record.requestedByAccountId(),
        record.requestedAt(),
        generatedAt,
        record.version() + 1L,
        record.createdAt(),
        generatedAt);
  }

  private OpComparisonAnalysisRecord withNarrativeResult(
      OpComparisonAnalysisRecord record,
      OpComparisonNarrativeStatus narrativeStatus,
      String observationsJson,
      String failureReason,
      Instant generatedAt) {
    return new OpComparisonAnalysisRecord(
        record.id(),
        record.incidentId(),
        record.operationalPeriodIdsJson(),
        record.requestHash(),
        record.sourceDataHash(),
        OpComparisonAnalysisStatus.READY,
        record.metricsJson(),
        record.diffFactsJson(),
        record.commonRegionsGeojson(),
        narrativeStatus,
        observationsJson,
        failureReason,
        record.requestedByAccountId(),
        record.requestedAt(),
        generatedAt,
        record.version() + 1L,
        record.createdAt(),
        generatedAt);
  }

  private void publishChanged(OpComparisonAnalysisRecord record) {
    eventHub.publish(
        new PublishRequest(
            eventIdFor(record),
            record.incidentId(),
            EVENT_TYPE,
            PAYLOAD_FORMAT_VERSION,
            SOURCE_ENTITY_TYPE,
            record.id(),
            clock.instant(),
            payloadFor(record)));
  }

  private UUID eventIdFor(OpComparisonAnalysisRecord record) {
    return UUID.nameUUIDFromBytes(
        ("event:" + EVENT_TYPE + ":" + record.id() + ":v" + record.version())
            .getBytes(StandardCharsets.UTF_8));
  }

  private Map<String, Object> payloadFor(OpComparisonAnalysisRecord record) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", record.id().toString());
    payload.put("comparisonId", record.id().toString());
    payload.put("incidentId", record.incidentId().toString());
    payload.put("operationalPeriodIds", parseOpIds(record.operationalPeriodIdsJson()));
    payload.put("status", record.status().name());
    payload.put("narrativeStatus", record.narrativeStatus().name());
    payload.put("sourceHash", record.sourceDataHash());
    payload.put("version", record.version());
    return payload;
  }

  private List<String> parseOpIds(String opIdsJson) {
    try {
      return objectMapper.readValue(
          opIdsJson,
          objectMapper
              .getTypeFactory()
              .constructCollectionType(List.class, String.class));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("OP comparison op id JSON cannot be parsed", exception);
    }
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw OpComparisonApiException.writeConflict();
    }
  }

  private OpComparisonResponse replayOrRun(
      String idempotencyKey, String fingerprint, Operation operation) {
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          ENDPOINT,
          idempotencyKey,
          fingerprint,
          202,
          OpComparisonResponse.class,
          operation::run,
          response ->
              new ResponseMetadata(
                  response.comparisonId().toString(),
                  response.status(),
                  response.version(),
                  0L));
    }
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing != null) {
      if (!existing.fingerprint().equals(fingerprint)) {
        throw OpComparisonApiException.idempotencyMismatch();
      }
      return existing.response();
    }
    OpComparisonResponse response = operation.run();
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(fingerprint, response));
    return response;
  }

  private String fingerprint(CreateOpComparisonRequest request) {
    return sha256(
        ENDPOINT
            + ":"
            + request.incidentId()
            + ":"
            + normalizedOpIds(request.operationalPeriodIds()));
  }

  private String requestHash(UUID incidentId, List<UUID> opIds, String sourceDataHash) {
    return sha256("op-comparison:" + incidentId + ":" + normalizedOpIds(opIds) + ":" + sourceDataHash);
  }

  private String normalizedOpIds(List<UUID> opIds) {
    return opIds == null
        ? ""
        : opIds.stream().map(UUID::toString).sorted().reduce((left, right) -> left + "," + right).orElse("");
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("OP comparison payload cannot be serialized", exception);
    }
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private record IdempotencyEntry(String fingerprint, OpComparisonResponse response) {}

  @FunctionalInterface
  private interface Operation {
    OpComparisonResponse run();
  }
}
