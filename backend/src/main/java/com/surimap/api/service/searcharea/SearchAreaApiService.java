package com.surimap.api.service.searcharea;

import com.surimap.api.controller.searcharea.request.AssignSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.CreateSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.PatchSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.SplitSearchAreaRequest;
import com.surimap.api.controller.searcharea.response.SearchAreaAssignmentResponse;
import com.surimap.api.controller.searcharea.response.SearchAreaCollectionResponse;
import com.surimap.api.controller.searcharea.response.SearchAreaReadResponse;
import com.surimap.api.controller.searcharea.response.SearchAreaResponse;
import com.surimap.api.controller.searcharea.response.SearchAreaSplitResponse;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.maparea.SearchAreaAssignmentMapper;
import com.surimap.maparea.SearchAreaAssignmentPersistenceRecord;
import com.surimap.maparea.SearchAreaHistoryPersistenceRecord;
import com.surimap.maparea.SearchAreaMapper;
import com.surimap.maparea.SearchAreaPersistenceRecord;
import com.surimap.maparea.SearchAreaReadRecord;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.geometry.validation.GeometryValidator;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchAreaApiService implements SearchAreaQuery {

  private static final String ACTIVE = "ACTIVE";
  private static final String CANCELLED = "CANCELLED";
  private static final String COMPLETED = "COMPLETED";
  private static final String OVERALL = "OVERALL";
  private static final String UNIT = "UNIT";
  private static final String TEAM = "TEAM";
  private static final int SRID = 4326;
  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  private final GeometryValidator geometryValidator;
  private final SearchAreaMapper searchAreaMapper;
  private final SearchAreaAssignmentMapper searchAreaAssignmentMapper;
  private final OperationalPeriodMapper operationalPeriodMapper;
  private final Map<UUID, SearchAreaRecord> searchAreas = new LinkedHashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new LinkedHashMap<>();

  public SearchAreaApiService(GeometryValidator geometryValidator) {
    this(
        geometryValidator,
        (SearchAreaMapper) null,
        (SearchAreaAssignmentMapper) null,
        (OperationalPeriodMapper) null);
  }

  @Autowired
  public SearchAreaApiService(
      GeometryValidator geometryValidator,
      ObjectProvider<SearchAreaMapper> searchAreaMapperProvider,
      ObjectProvider<SearchAreaAssignmentMapper> searchAreaAssignmentMapperProvider,
      ObjectProvider<OperationalPeriodMapper> operationalPeriodMapperProvider) {
    this(
        geometryValidator,
        searchAreaMapperProvider.getIfAvailable(),
        searchAreaAssignmentMapperProvider.getIfAvailable(),
        operationalPeriodMapperProvider.getIfAvailable());
  }

  private SearchAreaApiService(
      GeometryValidator geometryValidator,
      SearchAreaMapper searchAreaMapper,
      SearchAreaAssignmentMapper searchAreaAssignmentMapper,
      OperationalPeriodMapper operationalPeriodMapper) {
    this.geometryValidator = geometryValidator;
    this.searchAreaMapper = searchAreaMapper;
    this.searchAreaAssignmentMapper = searchAreaAssignmentMapper;
    this.operationalPeriodMapper = operationalPeriodMapper;
  }

  @Transactional
  public synchronized SearchAreaResponse create(
      CreateSearchAreaRequest request, String idempotencyKey) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("create", request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        SearchAreaResponse.class,
        () -> {
          String areaLevel = requireAreaLevel(request.areaLevel());
          validatePolygon(request.geometry());
          if (OVERALL.equals(areaLevel)) {
            if (activeOverallExists(request.incidentId())) {
              throw SearchAreaApiException.areaStateConflict();
            }
            if (persistenceAvailable()) {
              return createPersistentArea(request, areaLevel, null);
            }
          } else {
            requireOpId(request.opId());
            if (persistenceAvailable()) {
              return createPersistentChildArea(request, areaLevel);
            }
            requireActiveOverall(request.incidentId());
          }

          SearchAreaRecord created =
              new SearchAreaRecord(
                  UUID.randomUUID(),
                  request.incidentId(),
                  request.opId(),
                  null,
                  areaLevel,
                  ACTIVE,
                  1L,
                  1L,
                  request.geometry(),
                  Instant.now());
          searchAreas.put(created.id(), created);
          return toResponse(created);
        });
  }

  private SearchAreaResponse createPersistentChildArea(
      CreateSearchAreaRequest request, String areaLevel) {
    SearchAreaReadRecord activeOverall =
        findPersistentOverall(request.incidentId())
            .orElseThrow(SearchAreaApiException::overallSearchAreaRequired);
    if (!Objects.equals(activeOverall.operationalPeriodId(), request.opId())) {
      throw SearchAreaApiException.writeConflict();
    }
    return createPersistentArea(request, areaLevel, activeOverall.id());
  }

  private SearchAreaResponse createPersistentArea(
      CreateSearchAreaRequest request, String areaLevel, UUID parentSearchAreaId) {
    OperationalPeriod operationalPeriod =
        operationalPeriodMapper
            .findActiveByIncident(request.incidentId())
            .orElseThrow(SearchAreaApiException::overallSearchAreaRequired);

    Instant now = Instant.now();
    UUID id = UUID.randomUUID();
    UUID opId = operationalPeriod.getId();
    UUID createdByAccountId =
        actorAccountId().orElseGet(() -> fallbackCreatedByAccountId(operationalPeriod, request));
    SearchAreaPersistenceRecord row =
        new SearchAreaPersistenceRecord(
            id,
            opId,
            parentSearchAreaId,
            searchAreaName(areaLevel, request.memo()),
            areaLevel,
            toJtsPolygon(request.geometry()),
            ACTIVE,
            1L,
            createdByAccountId,
            now,
            now);

    searchAreaMapper.insert(row);
    searchAreaMapper.insertHistory(
        createdHistory(
            id,
            null,
            ACTIVE,
            null,
            row.geometry(),
            request.memo(),
            createdByAccountId,
            request.clientTs().toInstant(),
            now));

    SearchAreaRecord created =
        new SearchAreaRecord(
            id,
            request.incidentId(),
            opId,
            parentSearchAreaId,
            areaLevel,
            ACTIVE,
            1L,
            1L,
            request.geometry(),
            now);
    searchAreas.put(created.id(), created);
    return toResponse(created);
  }

  public synchronized SearchAreaReadResponse list(
      UUID incidentId, UUID opId, String areaLevel, String status) {
    if (OVERALL.equals(areaLevel) && ACTIVE.equals(status)) {
      return findPersistentOverall(incidentId)
          .map(this::toResponse)
          .or(
              () -> Optional.ofNullable(activeOverallOf(incidentId)).map(this::toResponse))
          .orElseThrow(SearchAreaApiException::overallSearchAreaRequired);
    }

    List<SearchAreaRecord> records =
        searchAreas.values().stream()
            .filter(area -> incidentId.equals(area.incidentId()))
            .filter(area -> opId == null || opId.equals(area.opId()))
            .filter(area -> areaLevel == null || areaLevel.equals(area.areaLevel()))
            .filter(area -> status == null || status.equals(area.status()))
            .filter(area -> status != null || !CANCELLED.equals(area.status()))
            .sorted(
                Comparator.comparing(
                        SearchAreaRecord::opId, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(SearchAreaRecord::status)
                    .thenComparing(SearchAreaRecord::updatedAt)
                    .thenComparing(SearchAreaRecord::id))
            .collect(Collectors.toList());
    long sourceVersion = records.stream().mapToLong(SearchAreaRecord::version).max().orElse(0L);
    return new SearchAreaCollectionResponse(
        incidentId, sourceVersion, records.stream().map(this::toResponse).toList());
  }

  @Override
  public synchronized Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
    Optional<OverallSearchAreaResult> persistentOverall =
        findPersistentOverall(incidentId).map(this::toOverallResult);
    if (persistentOverall.isPresent()) {
      return persistentOverall;
    }

    SearchAreaRecord overall = activeOverallOf(incidentId);
    if (overall == null) {
      return Optional.empty();
    }
    return Optional.of(
        new OverallSearchAreaResult(
            overall.id(),
            overall.incidentId(),
            overall.status(),
            overall.version(),
            overall.geometry(),
            computeBbox(overall.geometry()),
            overall.updatedAt()));
  }

  @Override
  public synchronized SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
    Map<UUID, SearchAreaRow> rows = new LinkedHashMap<>();
    if (persistentReadAvailable()) {
      searchAreaMapper.findByIncident(incidentId, filters).stream()
          .map(this::toSearchAreaRow)
          .forEach(row -> rows.put(row.id(), row));
    }
    queryRecords(incidentId, null, filters).stream()
        .map(this::toSearchAreaRow)
        .forEach(row -> rows.putIfAbsent(row.id(), row));
    long sourceVersion =
        rows.values().stream().mapToLong(SearchAreaRow::version).max().orElse(0L);
    return new SearchAreaCollection(incidentId, sourceVersion, List.copyOf(rows.values()));
  }

  @Override
  public synchronized SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
    SearchAreaFilters effectiveFilters = ignoreOpFilter(filters);
    Map<UUID, SearchAreaRow> rows = new LinkedHashMap<>();
    if (persistentReadAvailable()) {
      searchAreaMapper.findByOp(opId, effectiveFilters).stream()
          .map(this::toSearchAreaRow)
          .forEach(row -> rows.put(row.id(), row));
    }
    queryRecords(null, opId, effectiveFilters).stream()
        .map(this::toSearchAreaRow)
        .forEach(row -> rows.putIfAbsent(row.id(), row));
    long sourceVersion =
        rows.values().stream().mapToLong(SearchAreaRow::version).max().orElse(0L);
    UUID incidentId =
        rows.values().stream().map(SearchAreaRow::incidentId).findFirst().orElse(null);
    return new SearchAreaCollection(incidentId, sourceVersion, List.copyOf(rows.values()));
  }

  @Transactional
  public synchronized SearchAreaResponse patch(
      UUID searchAreaId, PatchSearchAreaRequest request, String idempotencyKey) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("patch:" + searchAreaId, request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        SearchAreaResponse.class,
        () -> {
          Optional<SearchAreaResponse> persistentResponse = patchPersistent(searchAreaId, request);
          if (persistentResponse.isPresent()) {
            return persistentResponse.orElseThrow();
          }

          SearchAreaRecord existing = requireArea(searchAreaId);
          if (request.expectedVersion() != null
              && existing.version() != request.expectedVersion()) {
            throw SearchAreaApiException.writeConflict();
          }
          GeoJsonPolygon geometry = existing.geometry();
          if (request.geometry() != null) {
            validatePolygon(request.geometry());
            if (!OVERALL.equals(existing.areaLevel())) {
              requireActiveOverall(existing.incidentId());
            }
            geometry = request.geometry();
          }
          String nextStatus = existing.status();
          if (request.nextStatus() != null) {
            requireOpId(request.opId());
            if (!Objects.equals(existing.opId(), request.opId())) {
              throw SearchAreaApiException.writeConflict();
            }
            nextStatus = requireStatus(request.nextStatus());
          }
          SearchAreaRecord updated =
              existing.withMutation(
                  nextStatus, existing.historyCount() + 1, existing.version() + 1, geometry);
          searchAreas.put(updated.id(), updated);
          return toResponse(updated);
        });
  }

  @Transactional
  public synchronized SearchAreaSplitResponse split(
      UUID searchAreaId, SplitSearchAreaRequest request, String idempotencyKey) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("split:" + searchAreaId, request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        SearchAreaSplitResponse.class,
        () -> {
          Optional<SearchAreaSplitResponse> persistentResponse =
              splitPersistent(searchAreaId, request);
          if (persistentResponse.isPresent()) {
            return persistentResponse.orElseThrow();
          }

          SearchAreaRecord parent = requireArea(searchAreaId);
          if (!Objects.equals(parent.opId(), request.opId())) {
            throw SearchAreaApiException.writeConflict();
          }
          if (parent.version() != request.expectedVersion()) {
            throw SearchAreaApiException.writeConflict();
          }
          if (request.children() == null || request.children().size() < 2) {
            throw SearchAreaApiException.invalidGeometry();
          }
          for (GeoJsonPolygon child : request.children()) {
            validatePolygon(child);
          }

          SearchAreaRecord cancelled =
              parent.withMutation(
                  CANCELLED, parent.historyCount() + 1, parent.version() + 1, parent.geometry());
          searchAreas.put(cancelled.id(), cancelled);

          List<SearchAreaRecord> children = new ArrayList<>();
          for (GeoJsonPolygon geometry : request.children()) {
            SearchAreaRecord child =
                new SearchAreaRecord(
                    UUID.randomUUID(),
                    parent.incidentId(),
                    parent.opId(),
                    parent.id(),
                    parent.areaLevel(),
                    ACTIVE,
                    1L,
                    1L,
                    geometry,
                    Instant.now());
            searchAreas.put(child.id(), child);
            children.add(child);
          }

          return new SearchAreaSplitResponse(
              cancelled.id(),
              toResponse(cancelled),
              children.stream().map(SearchAreaRecord::id).toList(),
              children.stream().map(this::toResponse).toList());
        });
  }

  @Transactional
  public synchronized SearchAreaAssignmentResponse assign(
      UUID searchAreaId, AssignSearchAreaRequest request, String idempotencyKey) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("assign:" + searchAreaId, request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        SearchAreaAssignmentResponse.class,
        () -> {
          Optional<SearchAreaAssignmentResponse> persistentResponse =
              assignPersistent(searchAreaId, request);
          if (persistentResponse.isPresent()) {
            return persistentResponse.orElseThrow();
          }

          SearchAreaRecord existing = requireArea(searchAreaId);
          if (!existing.incidentId().equals(request.incidentId())
              || !Objects.equals(existing.opId(), request.opId())) {
            throw SearchAreaApiException.writeConflict();
          }
          if (request.assigneeAccountIds() == null || request.assigneeAccountIds().isEmpty()) {
            throw SearchAreaApiException.writeConflict();
          }
          if (hasDuplicateAssignees(request.assigneeAccountIds())) {
            throw SearchAreaApiException.writeConflict();
          }
          SearchAreaRecord updated =
              existing.withMutation(
                  existing.status(),
                  existing.historyCount(),
                  existing.version() + 1,
                  existing.geometry());
          searchAreas.put(updated.id(), updated);
          List<UUID> assignmentIds =
              request.assigneeAccountIds().stream().map(ignored -> UUID.randomUUID()).toList();
          return new SearchAreaAssignmentResponse(
              updated.id(), updated.opId(), assignmentIds, updated.version());
        });
  }

  private Optional<SearchAreaAssignmentResponse> assignPersistent(
      UUID searchAreaId, AssignSearchAreaRequest request) {
    if (!persistentAssignmentAvailable()) {
      return Optional.empty();
    }
    Optional<SearchAreaReadRecord> existingOptional = searchAreaMapper.findById(searchAreaId);
    if (existingOptional.isEmpty()) {
      return Optional.empty();
    }

    SearchAreaReadRecord existing = existingOptional.orElseThrow();
    if (!existing.incidentId().equals(request.incidentId())
        || !Objects.equals(existing.operationalPeriodId(), request.opId())) {
      throw SearchAreaApiException.writeConflict();
    }
    if (request.assigneeAccountIds() == null || request.assigneeAccountIds().isEmpty()) {
      throw SearchAreaApiException.writeConflict();
    }
    if (hasDuplicateAssignees(request.assigneeAccountIds())) {
      throw SearchAreaApiException.writeConflict();
    }
    OperationalPeriod activeOperationalPeriod =
        operationalPeriodMapper
            .findActiveByIncident(existing.incidentId())
            .orElseThrow(SearchAreaApiException::writeConflict);
    if (!Objects.equals(activeOperationalPeriod.getId(), request.opId())) {
      throw SearchAreaApiException.writeConflict();
    }

    Instant now = Instant.now();
    Instant assignedAt = request.clientTs().toInstant();
    UUID assignedByAccountId =
        actorAccountId().orElseGet(() -> fallbackChangedByAccountId(existing.incidentId()));
    long nextVersion = existing.version() + 1;

    searchAreaAssignmentMapper.revokeActiveByArea(searchAreaId, now, now);
    List<UUID> assignmentIds = new ArrayList<>();
    for (UUID assigneeAccountId : request.assigneeAccountIds()) {
      UUID assignmentId = UUID.randomUUID();
      searchAreaAssignmentMapper.insert(
          new SearchAreaAssignmentPersistenceRecord(
              assignmentId,
              searchAreaId,
              assigneeAccountId,
              assignedByAccountId,
              assignedAt,
              null,
              ACTIVE,
              request.memo(),
              now,
              now));
      assignmentIds.add(assignmentId);
    }

    int updated =
        searchAreaMapper.updateMutation(
            searchAreaId, existing.status(), existing.geometry(), nextVersion, now);
    if (updated != 1) {
      throw SearchAreaApiException.writeConflict();
    }

    SearchAreaReadRecord updatedRecord =
        searchAreaMapper
            .findById(searchAreaId)
            .orElseThrow(SearchAreaApiException::writeConflict);
    SearchAreaRecord memoryRecord =
        new SearchAreaRecord(
            updatedRecord.id(),
            updatedRecord.incidentId(),
            updatedRecord.operationalPeriodId(),
            updatedRecord.parentSearchAreaId(),
            updatedRecord.areaLevel(),
            updatedRecord.status(),
            updatedRecord.historyCount(),
            updatedRecord.version(),
            toGeoJsonPolygon(updatedRecord.geometry()),
            updatedRecord.updatedAt());
    searchAreas.put(memoryRecord.id(), memoryRecord);

    return Optional.of(
        new SearchAreaAssignmentResponse(
            updatedRecord.id(),
            updatedRecord.operationalPeriodId(),
            assignmentIds,
            updatedRecord.version()));
  }

  private SearchAreaRecord activeOverallOf(UUID incidentId) {
    return searchAreas.values().stream()
        .filter(area -> incidentId.equals(area.incidentId()))
        .filter(area -> OVERALL.equals(area.areaLevel()))
        .filter(area -> ACTIVE.equals(area.status()))
        .findFirst()
        .orElse(null);
  }

  private boolean activeOverallExists(UUID incidentId) {
    if (activeOverallOf(incidentId) != null) {
      return true;
    }
    return persistenceAvailable()
        && searchAreaMapper.countActiveOverallByIncident(incidentId) > 0;
  }

  private boolean persistenceAvailable() {
    return searchAreaMapper != null && operationalPeriodMapper != null;
  }

  private boolean persistentReadAvailable() {
    return searchAreaMapper != null;
  }

  private boolean persistentAssignmentAvailable() {
    return searchAreaMapper != null
        && searchAreaAssignmentMapper != null
        && operationalPeriodMapper != null;
  }

  private boolean hasDuplicateAssignees(List<UUID> assigneeAccountIds) {
    return new HashSet<>(assigneeAccountIds).size() != assigneeAccountIds.size();
  }

  private Optional<SearchAreaResponse> patchPersistent(
      UUID searchAreaId, PatchSearchAreaRequest request) {
    if (!persistentReadAvailable()) {
      return Optional.empty();
    }
    Optional<SearchAreaReadRecord> existingOptional = searchAreaMapper.findById(searchAreaId);
    if (existingOptional.isEmpty()) {
      return Optional.empty();
    }

    SearchAreaReadRecord existing = existingOptional.orElseThrow();
    if (request.expectedVersion() != null && existing.version() != request.expectedVersion()) {
      throw SearchAreaApiException.writeConflict();
    }

    Polygon nextGeometry = existing.geometry();
    GeoJsonPolygon nextGeoJsonGeometry = toGeoJsonPolygon(existing.geometry());
    boolean geometryChanged = request.geometry() != null;
    if (geometryChanged) {
      validatePolygon(request.geometry());
      if (!OVERALL.equals(existing.areaLevel())) {
        findPersistentOverall(existing.incidentId())
            .orElseThrow(SearchAreaApiException::overallSearchAreaRequired);
      }
      nextGeometry = toJtsPolygon(request.geometry());
      nextGeoJsonGeometry = request.geometry();
    }

    String nextStatus = existing.status();
    boolean statusChanged = false;
    if (request.nextStatus() != null) {
      requireOpId(request.opId());
      if (!Objects.equals(existing.operationalPeriodId(), request.opId())) {
        throw SearchAreaApiException.writeConflict();
      }
      nextStatus = requireStatus(request.nextStatus());
      statusChanged = !Objects.equals(existing.status(), nextStatus);
    }

    Instant now = Instant.now();
    long nextVersion = existing.version() + 1;
    int updated =
        searchAreaMapper.updateMutation(searchAreaId, nextStatus, nextGeometry, nextVersion, now);
    if (updated != 1) {
      throw SearchAreaApiException.writeConflict();
    }

    UUID changedByAccountId =
        actorAccountId().orElseGet(() -> fallbackChangedByAccountId(existing.incidentId()));
    searchAreaMapper.insertHistory(
        new SearchAreaHistoryPersistenceRecord(
            UUID.randomUUID(),
            searchAreaId,
            statusChanged ? "STATUS_CHANGED" : "GEOMETRY_UPDATED",
            existing.status(),
            nextStatus,
            existing.geometry(),
            nextGeometry,
            request.memo(),
            changedByAccountId,
            request.clientTs().toInstant(),
            now));

    SearchAreaReadRecord updatedRecord =
        searchAreaMapper
            .findById(searchAreaId)
            .orElseThrow(SearchAreaApiException::writeConflict);
    SearchAreaRecord memoryRecord =
        new SearchAreaRecord(
            updatedRecord.id(),
            updatedRecord.incidentId(),
            updatedRecord.operationalPeriodId(),
            updatedRecord.parentSearchAreaId(),
            updatedRecord.areaLevel(),
            updatedRecord.status(),
            updatedRecord.historyCount(),
            updatedRecord.version(),
            nextGeoJsonGeometry,
            updatedRecord.updatedAt());
    searchAreas.put(memoryRecord.id(), memoryRecord);
    return Optional.of(toResponse(updatedRecord));
  }

  private Optional<SearchAreaSplitResponse> splitPersistent(
      UUID searchAreaId, SplitSearchAreaRequest request) {
    if (!persistentReadAvailable()) {
      return Optional.empty();
    }
    Optional<SearchAreaReadRecord> parentOptional = searchAreaMapper.findById(searchAreaId);
    if (parentOptional.isEmpty()) {
      return Optional.empty();
    }

    SearchAreaReadRecord parent = parentOptional.orElseThrow();
    if (!Objects.equals(parent.operationalPeriodId(), request.opId())) {
      throw SearchAreaApiException.writeConflict();
    }
    if (request.expectedVersion() == null || parent.version() != request.expectedVersion()) {
      throw SearchAreaApiException.writeConflict();
    }
    if (request.children() == null || request.children().size() < 2) {
      throw SearchAreaApiException.invalidGeometry();
    }
    for (GeoJsonPolygon child : request.children()) {
      validatePolygon(child);
    }

    Instant now = Instant.now();
    UUID changedByAccountId =
        actorAccountId().orElseGet(() -> fallbackChangedByAccountId(parent.incidentId()));
    long parentNextVersion = parent.version() + 1;
    int updated =
        searchAreaMapper.updateMutation(
            searchAreaId, CANCELLED, parent.geometry(), parentNextVersion, now);
    if (updated != 1) {
      throw SearchAreaApiException.writeConflict();
    }
    searchAreaMapper.insertHistory(
        new SearchAreaHistoryPersistenceRecord(
            UUID.randomUUID(),
            searchAreaId,
            "STATUS_CHANGED",
            parent.status(),
            CANCELLED,
            parent.geometry(),
            parent.geometry(),
            request.memo(),
            changedByAccountId,
            request.clientTs().toInstant(),
            now));

    List<SearchAreaResponse> children = new ArrayList<>();
    for (GeoJsonPolygon childGeometry : request.children()) {
      UUID childId = UUID.randomUUID();
      Polygon childPolygon = toJtsPolygon(childGeometry);
      SearchAreaPersistenceRecord childRow =
          new SearchAreaPersistenceRecord(
              childId,
              parent.operationalPeriodId(),
              parent.id(),
              searchAreaName(parent.areaLevel(), request.memo()),
              parent.areaLevel(),
              childPolygon,
              ACTIVE,
              1L,
              changedByAccountId,
              now,
              now);
      searchAreaMapper.insert(childRow);
      searchAreaMapper.insertHistory(
          new SearchAreaHistoryPersistenceRecord(
              UUID.randomUUID(),
              childId,
              "SPLIT",
              null,
              ACTIVE,
              null,
              childPolygon,
              request.memo(),
              changedByAccountId,
              request.clientTs().toInstant(),
              now));
      SearchAreaReadRecord childRecord =
          searchAreaMapper.findById(childId).orElseThrow(SearchAreaApiException::writeConflict);
      SearchAreaRecord memoryChild =
          new SearchAreaRecord(
              childRecord.id(),
              childRecord.incidentId(),
              childRecord.operationalPeriodId(),
              childRecord.parentSearchAreaId(),
              childRecord.areaLevel(),
              childRecord.status(),
              childRecord.historyCount(),
              childRecord.version(),
              childGeometry,
              childRecord.updatedAt());
      searchAreas.put(memoryChild.id(), memoryChild);
      children.add(toResponse(childRecord));
    }

    SearchAreaReadRecord updatedParent =
        searchAreaMapper
            .findById(searchAreaId)
            .orElseThrow(SearchAreaApiException::writeConflict);
    SearchAreaRecord memoryParent =
        new SearchAreaRecord(
            updatedParent.id(),
            updatedParent.incidentId(),
            updatedParent.operationalPeriodId(),
            updatedParent.parentSearchAreaId(),
            updatedParent.areaLevel(),
            updatedParent.status(),
            updatedParent.historyCount(),
            updatedParent.version(),
            toGeoJsonPolygon(updatedParent.geometry()),
            updatedParent.updatedAt());
    searchAreas.put(memoryParent.id(), memoryParent);

    return Optional.of(
        new SearchAreaSplitResponse(
            updatedParent.id(),
            toResponse(updatedParent),
            children.stream().map(SearchAreaResponse::id).toList(),
            children));
  }

  private Optional<SearchAreaReadRecord> findPersistentOverall(UUID incidentId) {
    if (!persistentReadAvailable()) {
      return Optional.empty();
    }
    return searchAreaMapper.findActiveOverallByIncident(incidentId);
  }

  private SearchAreaRecord requireActiveOverall(UUID incidentId) {
    SearchAreaRecord overall = activeOverallOf(incidentId);
    if (overall == null) {
      throw SearchAreaApiException.overallSearchAreaRequired();
    }
    return overall;
  }

  private SearchAreaRecord requireArea(UUID searchAreaId) {
    SearchAreaRecord record = searchAreas.get(searchAreaId);
    if (record == null) {
      throw SearchAreaApiException.writeConflict();
    }
    return record;
  }

  private void validatePolygon(GeoJsonPolygon polygon) {
    if (polygon == null || !"Polygon".equals(polygon.type())) {
      throw SearchAreaApiException.invalidGeometry();
    }
    geometryValidator.validateAndCanonicalizePolygonRing(polygon.outerRing());
  }

  private String requireAreaLevel(String areaLevel) {
    if (OVERALL.equals(areaLevel) || UNIT.equals(areaLevel) || TEAM.equals(areaLevel)) {
      return areaLevel;
    }
    throw SearchAreaApiException.writeConflict();
  }

  private String requireStatus(String status) {
    if (ACTIVE.equals(status) || COMPLETED.equals(status) || CANCELLED.equals(status)) {
      return status;
    }
    throw SearchAreaApiException.areaStateConflict();
  }

  private void requireOpId(UUID opId) {
    if (opId == null) {
      throw SearchAreaApiException.writeConflict();
    }
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw SearchAreaApiException.writeConflict();
    }
  }

  private SearchAreaResponse toResponse(SearchAreaRecord record) {
    return new SearchAreaResponse(
        record.id(),
        record.incidentId(),
        record.opId(),
        record.parentAreaId(),
        record.areaLevel(),
        record.status(),
        record.historyCount(),
        record.version(),
        record.geometry(),
        computeBbox(record.geometry()),
        record.updatedAt());
  }

  private SearchAreaResponse toResponse(SearchAreaReadRecord record) {
    GeoJsonPolygon geometry = toGeoJsonPolygon(record.geometry());
    return new SearchAreaResponse(
        record.id(),
        record.incidentId(),
        record.operationalPeriodId(),
        record.parentSearchAreaId(),
        record.areaLevel(),
        record.status(),
        record.historyCount(),
        record.version(),
        geometry,
        computeBbox(geometry),
        record.updatedAt());
  }

  private List<SearchAreaRecord> queryRecords(
      UUID incidentId, UUID opId, SearchAreaFilters filters) {
    SearchAreaFilters effectiveFilters = filters == null ? SearchAreaFilters.empty() : filters;
    return searchAreas.values().stream()
        .filter(area -> incidentId == null || incidentId.equals(area.incidentId()))
        .filter(area -> opId == null || opId.equals(area.opId()))
        .filter(
            area -> effectiveFilters.opId() == null || effectiveFilters.opId().equals(area.opId()))
        .filter(area -> effectiveFilters.includeCancelled() || !CANCELLED.equals(area.status()))
        .filter(
            area ->
                effectiveFilters.status() == null
                    || effectiveFilters.status().contains(area.status()))
        .filter(
            area ->
                effectiveFilters.minVersion() == null
                    || area.version() >= effectiveFilters.minVersion())
        .filter(
            area ->
                effectiveFilters.updatedAfter() == null
                    || area.updatedAt().isAfter(effectiveFilters.updatedAfter()))
        .filter(
            area ->
                effectiveFilters.bbox() == null
                    || bboxIntersects(computeBbox(area.geometry()), effectiveFilters.bbox()))
        .sorted(
            Comparator.comparing(
                    SearchAreaRecord::opId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SearchAreaRecord::status)
                .thenComparing(SearchAreaRecord::updatedAt)
                .thenComparing(SearchAreaRecord::id))
        .toList();
  }

  private SearchAreaRow toSearchAreaRow(SearchAreaRecord record) {
    return new SearchAreaRow(
        record.id(),
        record.incidentId(),
        record.opId(),
        record.parentAreaId(),
        record.status(),
        record.areaLevel(),
        record.version(),
        record.geometry(),
        computeBbox(record.geometry()),
        record.updatedAt(),
        record.historyCount());
  }

  private SearchAreaRow toSearchAreaRow(SearchAreaReadRecord record) {
    GeoJsonPolygon geometry = toGeoJsonPolygon(record.geometry());
    return new SearchAreaRow(
        record.id(),
        record.incidentId(),
        record.operationalPeriodId(),
        record.parentSearchAreaId(),
        record.status(),
        record.areaLevel(),
        record.version(),
        geometry,
        computeBbox(geometry),
        record.updatedAt(),
        record.historyCount());
  }

  private OverallSearchAreaResult toOverallResult(SearchAreaReadRecord record) {
    GeoJsonPolygon geometry = toGeoJsonPolygon(record.geometry());
    return new OverallSearchAreaResult(
        record.id(),
        record.incidentId(),
        record.status(),
        record.version(),
        geometry,
        computeBbox(geometry),
        record.updatedAt());
  }

  private SearchAreaFilters ignoreOpFilter(SearchAreaFilters filters) {
    if (filters == null || filters.opId() == null) {
      return filters;
    }
    return new SearchAreaFilters(
        filters.status(),
        null,
        filters.bbox(),
        filters.minVersion(),
        filters.updatedAfter(),
        filters.includeCancelled());
  }

  private UUID fallbackCreatedByAccountId(
      OperationalPeriod operationalPeriod, CreateSearchAreaRequest request) {
    if (operationalPeriod.getStartedByAccountId() != null) {
      return operationalPeriod.getStartedByAccountId();
    }
    return request.incidentId();
  }

  private UUID fallbackChangedByAccountId(UUID incidentId) {
    if (operationalPeriodMapper == null) {
      return incidentId;
    }
    return operationalPeriodMapper
        .findActiveByIncident(incidentId)
        .map(OperationalPeriod::getStartedByAccountId)
        .filter(Objects::nonNull)
        .orElse(incidentId);
  }

  private Optional<UUID> actorAccountId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof SuriMapAuthentication suriMapAuthentication) {
      try {
        return Optional.of(UUID.fromString(suriMapAuthentication.getAccountId()));
      } catch (IllegalArgumentException ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private Polygon toJtsPolygon(GeoJsonPolygon geoJsonPolygon) {
    Coordinate[] coordinates =
        geoJsonPolygon.outerRing().stream()
            .map(point -> new Coordinate(point.get(0).doubleValue(), point.get(1).doubleValue()))
            .toArray(Coordinate[]::new);
    LinearRing shell = GEOMETRY_FACTORY.createLinearRing(coordinates);
    Polygon polygon = GEOMETRY_FACTORY.createPolygon(shell);
    polygon.setSRID(SRID);
    return polygon;
  }

  private SearchAreaHistoryPersistenceRecord createdHistory(
      UUID searchAreaId,
      String previousStatus,
      String nextStatus,
      Polygon previousGeometry,
      Polygon nextGeometry,
      String memo,
      UUID changedByAccountId,
      Instant changedAt,
      Instant createdAt) {
    return new SearchAreaHistoryPersistenceRecord(
        UUID.randomUUID(),
        searchAreaId,
        "CREATED",
        previousStatus,
        nextStatus,
        previousGeometry,
        nextGeometry,
        memo,
        changedByAccountId,
        changedAt,
        createdAt);
  }

  private String searchAreaName(String areaLevel, String memo) {
    if (memo != null && !memo.isBlank()) {
      return memo;
    }
    return areaLevel;
  }

  private GeoJsonPolygon toGeoJsonPolygon(Polygon polygon) {
    List<List<List<BigDecimal>>> rings = new ArrayList<>();
    rings.add(toGeoJsonRing(polygon.getExteriorRing().getCoordinates()));
    for (int index = 0; index < polygon.getNumInteriorRing(); index++) {
      rings.add(toGeoJsonRing(polygon.getInteriorRingN(index).getCoordinates()));
    }
    return new GeoJsonPolygon("Polygon", rings);
  }

  private List<List<BigDecimal>> toGeoJsonRing(Coordinate[] coordinates) {
    List<List<BigDecimal>> ring = new ArrayList<>(coordinates.length);
    for (Coordinate coordinate : coordinates) {
      ring.add(List.of(BigDecimal.valueOf(coordinate.x), BigDecimal.valueOf(coordinate.y)));
    }
    return ring;
  }

  private List<BigDecimal> computeBbox(GeoJsonPolygon polygon) {
    BigDecimal minLon = null;
    BigDecimal minLat = null;
    BigDecimal maxLon = null;
    BigDecimal maxLat = null;
    for (List<List<BigDecimal>> ring : polygon.coordinates()) {
      for (List<BigDecimal> point : ring) {
        BigDecimal lon = point.get(0);
        BigDecimal lat = point.get(1);
        if (minLon == null || lon.compareTo(minLon) < 0) minLon = lon;
        if (maxLon == null || lon.compareTo(maxLon) > 0) maxLon = lon;
        if (minLat == null || lat.compareTo(minLat) < 0) minLat = lat;
        if (maxLat == null || lat.compareTo(maxLat) > 0) maxLat = lat;
      }
    }
    return List.of(minLon, minLat, maxLon, maxLat);
  }

  private boolean bboxIntersects(List<BigDecimal> areaBbox, List<BigDecimal> filterBbox) {
    if (filterBbox.size() != 4) {
      return false;
    }
    BigDecimal aMinLon = areaBbox.get(0);
    BigDecimal aMinLat = areaBbox.get(1);
    BigDecimal aMaxLon = areaBbox.get(2);
    BigDecimal aMaxLat = areaBbox.get(3);
    BigDecimal fMinLon = filterBbox.get(0);
    BigDecimal fMinLat = filterBbox.get(1);
    BigDecimal fMaxLon = filterBbox.get(2);
    BigDecimal fMaxLat = filterBbox.get(3);
    return aMaxLon.compareTo(fMinLon) >= 0
        && aMinLon.compareTo(fMaxLon) <= 0
        && aMaxLat.compareTo(fMinLat) >= 0
        && aMinLat.compareTo(fMaxLat) <= 0;
  }

  private <T> T replayOrRun(
      String idempotencyKey, String fingerprint, Class<T> responseType, Operation<T> operation) {
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing != null) {
      if (!existing.fingerprint().equals(fingerprint)) {
        throw SearchAreaApiException.idempotencyMismatch();
      }
      if (!responseType.isInstance(existing.response())) {
        throw SearchAreaApiException.writeConflict();
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

  private record SearchAreaRecord(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID parentAreaId,
      String areaLevel,
      String status,
      long historyCount,
      long version,
      GeoJsonPolygon geometry,
      Instant updatedAt) {

    SearchAreaRecord withMutation(
        String nextStatus, long nextHistoryCount, long nextVersion, GeoJsonPolygon nextGeometry) {
      return new SearchAreaRecord(
          id,
          incidentId,
          opId,
          parentAreaId,
          areaLevel,
          nextStatus,
          nextHistoryCount,
          nextVersion,
          nextGeometry,
          Instant.now());
    }
  }

  private record IdempotencyEntry(String fingerprint, Object response) {}

  @FunctionalInterface
  private interface Operation<T> {
    T run();
  }
}
