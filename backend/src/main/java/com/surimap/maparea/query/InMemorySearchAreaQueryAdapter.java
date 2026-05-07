package com.surimap.maparea.query;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InMemorySearchAreaQueryAdapter implements SearchAreaQuery {

  private record OverallEntry(
      UUID id,
      UUID incidentId,
      String status,
      long version,
      GeoJsonPolygon geometry,
      Instant updatedAt) {}

  private record AreaEntry(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID parentAreaId,
      String status,
      long version,
      GeoJsonPolygon geometry,
      Instant createdAt,
      Instant updatedAt) {}

  private final ConcurrentHashMap<UUID, OverallEntry> overallStore = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, AreaEntry> areaStore = new ConcurrentHashMap<>();
  // opId -> incidentId mapping for byOp empty-collection incidentId resolution
  private final ConcurrentHashMap<UUID, UUID> opIncidentIndex = new ConcurrentHashMap<>();

  public void registerOverall(
      UUID id,
      UUID incidentId,
      String status,
      long version,
      GeoJsonPolygon geometry,
      Instant updatedAt) {
    overallStore.put(
        incidentId, new OverallEntry(id, incidentId, status, version, geometry, updatedAt));
  }

  public void registerArea(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID parentAreaId,
      String status,
      long version,
      GeoJsonPolygon geometry,
      Instant createdAt,
      Instant updatedAt) {
    areaStore.put(
        id,
        new AreaEntry(
            id, incidentId, opId, parentAreaId, status, version, geometry, createdAt, updatedAt));
    if (opId != null) opIncidentIndex.put(opId, incidentId);
  }

  @Override
  public Optional<OverallSearchAreaResult> overallOf(UUID incidentId) {
    OverallEntry e = overallStore.get(incidentId);
    if (e == null || !"ACTIVE".equals(e.status())) return Optional.empty();
    return Optional.of(
        new OverallSearchAreaResult(
            e.id(),
            e.incidentId(),
            e.status(),
            e.version(),
            e.geometry(),
            computeBbox(e.geometry()),
            e.updatedAt()));
  }

  @Override
  public SearchAreaCollection byIncident(UUID incidentId, SearchAreaFilters filters) {
    Stream<AreaEntry> stream =
        areaStore.values().stream().filter(e -> incidentId.equals(e.incidentId()));
    stream = applyFilters(stream, filters);
    List<AreaEntry> entries =
        stream
            .sorted(
                Comparator.comparing(
                        AreaEntry::opId, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(AreaEntry::status)
                    .thenComparing(AreaEntry::createdAt)
                    .thenComparing(AreaEntry::id))
            .collect(Collectors.toList());
    long sourceVersion = entries.stream().mapToLong(AreaEntry::version).max().orElse(0L);
    List<SearchAreaRow> rows = entries.stream().map(this::toRow).collect(Collectors.toList());
    return new SearchAreaCollection(incidentId, sourceVersion, rows);
  }

  @Override
  public SearchAreaCollection byOp(UUID opId, SearchAreaFilters filters) {
    Stream<AreaEntry> stream = areaStore.values().stream().filter(e -> opId.equals(e.opId()));
    stream = applyFilters(stream, filters);
    List<AreaEntry> entries =
        stream
            .sorted(
                Comparator.comparing(AreaEntry::status)
                    .thenComparing(AreaEntry::createdAt)
                    .thenComparing(AreaEntry::id))
            .collect(Collectors.toList());
    long sourceVersion = entries.stream().mapToLong(AreaEntry::version).max().orElse(0L);
    UUID incidentId = entries.isEmpty() ? opIncidentIndex.get(opId) : entries.get(0).incidentId();
    List<SearchAreaRow> rows = entries.stream().map(this::toRow).collect(Collectors.toList());
    return new SearchAreaCollection(incidentId, sourceVersion, rows);
  }

  private Stream<AreaEntry> applyFilters(Stream<AreaEntry> stream, SearchAreaFilters filters) {
    if (!filters.includeCancelled()) {
      stream = stream.filter(e -> !"CANCELLED".equals(e.status()));
    }
    if (filters.status() != null && !filters.status().isEmpty()) {
      stream = stream.filter(e -> filters.status().contains(e.status()));
    }
    if (filters.opId() != null) {
      stream = stream.filter(e -> filters.opId().equals(e.opId()));
    }
    if (filters.minVersion() != null) {
      stream = stream.filter(e -> e.version() >= filters.minVersion());
    }
    if (filters.updatedAfter() != null) {
      stream = stream.filter(e -> e.updatedAt().isAfter(filters.updatedAfter()));
    }
    if (filters.bbox() != null && filters.bbox().size() == 4) {
      stream = stream.filter(e -> bboxIntersects(computeBbox(e.geometry()), filters.bbox()));
    }
    return stream;
  }

  private SearchAreaRow toRow(AreaEntry e) {
    return new SearchAreaRow(
        e.id(),
        e.incidentId(),
        e.opId(),
        e.parentAreaId(),
        e.status(),
        e.version(),
        e.geometry(),
        computeBbox(e.geometry()),
        e.updatedAt(),
        0L);
  }

  private boolean bboxIntersects(List<BigDecimal> areaBbox, List<BigDecimal> filterBbox) {
    // areaBbox/filterBbox: [minLon, minLat, maxLon, maxLat]
    BigDecimal aMinLon = areaBbox.get(0), aMinLat = areaBbox.get(1);
    BigDecimal aMaxLon = areaBbox.get(2), aMaxLat = areaBbox.get(3);
    BigDecimal fMinLon = filterBbox.get(0), fMinLat = filterBbox.get(1);
    BigDecimal fMaxLon = filterBbox.get(2), fMaxLat = filterBbox.get(3);
    return aMaxLon.compareTo(fMinLon) >= 0
        && aMinLon.compareTo(fMaxLon) <= 0
        && aMaxLat.compareTo(fMinLat) >= 0
        && aMinLat.compareTo(fMaxLat) <= 0;
  }

  private List<BigDecimal> computeBbox(GeoJsonPolygon polygon) {
    BigDecimal minLon = null, minLat = null, maxLon = null, maxLat = null;
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
}
