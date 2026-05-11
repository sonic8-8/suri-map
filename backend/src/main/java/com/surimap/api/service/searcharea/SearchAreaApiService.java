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
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.maparea.geometry.validation.GeometryValidator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SearchAreaApiService implements SearchAreaQuery {

  private static final String ACTIVE = "ACTIVE";
  private static final String CANCELLED = "CANCELLED";
  private static final String COMPLETED = "COMPLETED";
  private static final String OVERALL = "OVERALL";
  private static final String UNIT = "UNIT";
  private static final String TEAM = "TEAM";

  private final GeometryValidator geometryValidator;
  private final Map<UUID, SearchAreaRecord> searchAreas = new LinkedHashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new LinkedHashMap<>();

  public SearchAreaApiService(GeometryValidator geometryValidator) {
    this.geometryValidator = geometryValidator;
  }

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
            if (activeOverallOf(request.incidentId()) != null) {
              throw SearchAreaApiException.areaStateConflict();
            }
          } else {
            requireOpId(request.opId());
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

  public synchronized SearchAreaReadResponse list(
      UUID incidentId, UUID opId, String areaLevel, String status) {
    if (OVERALL.equals(areaLevel) && ACTIVE.equals(status)) {
      SearchAreaRecord overall = activeOverallOf(incidentId);
      if (overall == null) {
        throw SearchAreaApiException.overallSearchAreaRequired();
      }
      return toResponse(overall);
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
    List<SearchAreaRecord> records = queryRecords(incidentId, null, filters);
    long sourceVersion = records.stream().mapToLong(SearchAreaRecord::version).max().orElse(0L);
    return new SearchAreaCollection(
        incidentId, sourceVersion, records.stream().map(this::toSearchAreaRow).toList());
  }

  @Override
  public synchronized SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
    List<SearchAreaRecord> records = queryRecords(null, opId, filters);
    long sourceVersion = records.stream().mapToLong(SearchAreaRecord::version).max().orElse(0L);
    UUID incidentId = records.isEmpty() ? null : records.get(0).incidentId();
    return new SearchAreaCollection(
        incidentId, sourceVersion, records.stream().map(this::toSearchAreaRow).toList());
  }

  public synchronized SearchAreaResponse patch(
      UUID searchAreaId, PatchSearchAreaRequest request, String idempotencyKey) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("patch:" + searchAreaId, request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        SearchAreaResponse.class,
        () -> {
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

  public synchronized SearchAreaSplitResponse split(
      UUID searchAreaId, SplitSearchAreaRequest request, String idempotencyKey) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("split:" + searchAreaId, request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        SearchAreaSplitResponse.class,
        () -> {
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

  public synchronized SearchAreaAssignmentResponse assign(
      UUID searchAreaId, AssignSearchAreaRequest request, String idempotencyKey) {
    requireIdempotencyKey(idempotencyKey);
    String fingerprint = fingerprint("assign:" + searchAreaId, request);
    return replayOrRun(
        idempotencyKey,
        fingerprint,
        SearchAreaAssignmentResponse.class,
        () -> {
          SearchAreaRecord existing = requireArea(searchAreaId);
          if (!existing.incidentId().equals(request.incidentId())
              || !Objects.equals(existing.opId(), request.opId())) {
            throw SearchAreaApiException.writeConflict();
          }
          if (request.assigneeAccountIds() == null || request.assigneeAccountIds().isEmpty()) {
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

  private SearchAreaRecord activeOverallOf(UUID incidentId) {
    return searchAreas.values().stream()
        .filter(area -> incidentId.equals(area.incidentId()))
        .filter(area -> OVERALL.equals(area.areaLevel()))
        .filter(area -> ACTIVE.equals(area.status()))
        .findFirst()
        .orElse(null);
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

  private List<SearchAreaRecord> queryRecords(
      UUID incidentId, UUID opId, SearchAreaFilters filters) {
    SearchAreaFilters effectiveFilters = filters == null ? SearchAreaFilters.empty() : filters;
    return searchAreas.values().stream()
        .filter(area -> incidentId == null || incidentId.equals(area.incidentId()))
        .filter(area -> opId == null || opId.equals(area.opId()))
        .filter(area -> effectiveFilters.opId() == null || effectiveFilters.opId().equals(area.opId()))
        .filter(area -> effectiveFilters.includeCancelled() || !CANCELLED.equals(area.status()))
        .filter(area -> effectiveFilters.status() == null || effectiveFilters.status().contains(area.status()))
        .filter(area -> effectiveFilters.minVersion() == null || area.version() >= effectiveFilters.minVersion())
        .filter(area -> effectiveFilters.updatedAfter() == null || area.updatedAt().isAfter(effectiveFilters.updatedAfter()))
        .filter(area -> effectiveFilters.bbox() == null || bboxIntersects(computeBbox(area.geometry()), effectiveFilters.bbox()))
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
        record.version(),
        record.geometry(),
        computeBbox(record.geometry()),
        record.updatedAt(),
        record.historyCount());
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
