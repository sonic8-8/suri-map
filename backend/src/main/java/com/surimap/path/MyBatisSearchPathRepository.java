package com.surimap.path;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class MyBatisSearchPathRepository implements SearchPathRepository {

  private static final int SRID = 4326;
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), SRID);

  private final SearchPathMapper mapper;

  public MyBatisSearchPathRepository(SearchPathMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<SearchPathAggregate> findById(UUID pathId) {
    return mapper.findPathById(pathId).map(this::toAggregate);
  }

  @Override
  public SearchPathAggregate save(SearchPathAggregate aggregate) {
    Instant now = Instant.now();
    ResolvedDutyShift dutyShift = resolveDutyShift(aggregate);
    Geometry geometry = lineStringOrNull(aggregate.points());
    Instant startedAt = startedAt(aggregate, now);
    SearchPathPersistenceRecord record =
        new SearchPathPersistenceRecord(
            aggregate.id(),
            dutyShift.id(),
            dutyShift.accountId(),
            aggregate.status().name(),
            startedAt,
            aggregate.endedAt(),
            geometry,
            aggregate.version(),
            startedAt,
            now);

    if (mapper.findPathById(aggregate.id()).isPresent()) {
      mapper.updatePath(record);
    } else {
      mapper.insertPath(record);
    }

    List<SearchPathSegment> persistedSegments = persistSegments(aggregate, now);
    persistExcludedPoints(aggregate, now);
    return new SearchPathAggregate(
        aggregate.id(),
        dutyShift.id(),
        aggregate.incidentId(),
        aggregate.opId(),
        aggregate.policePhoneId(),
        dutyShift.accountId(),
        aggregate.status(),
        aggregate.version(),
        aggregate.points(),
        aggregate.excludedPoints(),
        persistedSegments);
  }

  @Override
  public List<SearchPathAggregate> findAll() {
    return mapper.findAllPaths().stream().map(this::toAggregate).toList();
  }

  @Override
  public List<SearchPathAggregate> findByQuery(
      UUID incidentId, UUID opId, UUID policePhoneId, UUID accountId) {
    return mapper.findPaths(incidentId, opId, policePhoneId, accountId).stream()
        .map(this::toAggregate)
        .toList();
  }

  private List<SearchPathSegment> persistSegments(SearchPathAggregate aggregate, Instant now) {
    mapper.deleteSegments(aggregate.id());
    List<SearchPathPoint> points = aggregate.points();
    List<SearchPathSegment> persistedSegments = new ArrayList<>();
    for (SearchPathSegment segment : aggregate.segments()) {
      UUID segmentId = uuidSegmentId(aggregate.id(), segment);
      LineString geometry = segmentLineString(points, segment);
      Instant startedAt = instant(points.get(segment.startIndex()).clientTs());
      Instant endedAt = instant(points.get(segment.endIndex()).clientTs());
      mapper.insertSegment(
          new SearchPathSegmentPersistenceRecord(
              segmentId,
              aggregate.id(),
              segment.movementType().name(),
              segment.movementTypeSource().name(),
              geometry,
              startedAt,
              endedAt,
              segment.correctedByAccountId(),
              instant(segment.correctedAt()),
              segment.version(),
              now,
              now));
      persistedSegments.add(
          new SearchPathSegment(
              segmentId.toString(),
              segment.version(),
              segment.movementType(),
              segment.movementTypeSource(),
              segment.startIndex(),
              segment.endIndex(),
              segment.startPointId(),
              segment.endPointId(),
              segment.correctedByAccountId(),
              segment.correctedAt()));
    }
    return List.copyOf(persistedSegments);
  }

  private SearchPathAggregate toAggregate(SearchPathReadRecord record) {
    List<SearchPathPoint> points = pointsFrom(record.geometry(), record.startedAt());
    List<SearchPathSegmentReadRecord> segmentRecords = mapper.findSegmentsByPathId(record.id());
    List<SearchPathSegment> segments = segmentsFrom(segmentRecords, points);
    List<PathExcludedPoint> excludedPoints = excludedPointsFrom(record.id());
    return new SearchPathAggregate(
        record.id(),
        record.dutyShiftId(),
        record.incidentId(),
        record.opId(),
        record.policePhoneId(),
        record.accountId(),
        record.startedAt(),
        record.endedAt(),
        SearchPathStatus.valueOf(record.status()),
        record.version(),
        points,
        excludedPoints,
        segments);
  }

  private ResolvedDutyShift resolveDutyShift(SearchPathAggregate aggregate) {
    UUID accountId = aggregate.accountId();
    if (accountId != null) {
      UUID dutyShiftId =
          mapper
              .findActiveDutyShiftIdByAccount(aggregate.opId(), accountId)
              .orElseThrow(() -> new SearchPathApiException("police_phone_not_assigned"));
      return new ResolvedDutyShift(dutyShiftId, accountId);
    }
    UUID dutyShiftId =
        mapper
            .findActiveDutyShiftId(aggregate.opId(), aggregate.policePhoneId())
            .orElseThrow(() -> new SearchPathApiException("police_phone_not_assigned"));
    UUID inferredAccountId =
        mapper
            .findActiveDutyShiftAccountId(aggregate.opId(), aggregate.policePhoneId())
            .orElseThrow(() -> new SearchPathApiException("police_phone_not_assigned"));
    return new ResolvedDutyShift(dutyShiftId, inferredAccountId);
  }

  private void persistExcludedPoints(SearchPathAggregate aggregate, Instant now) {
    mapper.deleteExcludedPoints(aggregate.id());
    for (PathExcludedPoint point : aggregate.excludedPoints()) {
      mapper.insertExcludedPoint(
          new SearchPathExcludedPointPersistenceRecord(
              excludedPointId(aggregate.id(), point),
              aggregate.id(),
              point.pointId(),
              point.reason(),
              instant(point.clientTs()),
              now,
              now));
    }
  }

  private List<PathExcludedPoint> excludedPointsFrom(UUID pathId) {
    return mapper.findExcludedPointsByPathId(pathId).stream()
        .map(
            record ->
                new PathExcludedPoint(
                    record.pointId(), record.reason(), offsetDateTime(record.clientTs())))
        .toList();
  }

  private List<SearchPathSegment> segmentsFrom(
      List<SearchPathSegmentReadRecord> records, List<SearchPathPoint> points) {
    List<SearchPathSegment> segments = new ArrayList<>();
    int startIndex = 0;
    for (SearchPathSegmentReadRecord record : records) {
      SegmentIndexes indexes = segmentIndexes(record, points, startIndex);
      segments.add(
          new SearchPathSegment(
              record.id().toString(),
              record.version(),
              MovementType.valueOf(record.movementType()),
              MovementTypeSource.valueOf(record.movementTypeSource()),
              indexes.start(),
              indexes.end(),
              "db-point-%03d".formatted(indexes.start() + 1),
              "db-point-%03d".formatted(indexes.end() + 1),
              record.correctedByAccountId(),
              offsetDateTime(record.correctedAt())));
      startIndex = indexes.end() + 1;
    }
    return segments.stream()
        .sorted(Comparator.comparingInt(SearchPathSegment::startIndex))
        .toList();
  }

  private SegmentIndexes segmentIndexes(
      SearchPathSegmentReadRecord record, List<SearchPathPoint> points, int fallbackStart) {
    Coordinate[] coordinates = effectiveCoordinates(record);
    if (coordinates.length > 0 && !points.isEmpty()) {
      int maxStart = points.size() - coordinates.length;
      int preferredStart = Math.max(0, Math.min(fallbackStart, maxStart));
      for (int candidate = preferredStart; candidate <= maxStart; candidate++) {
        if (matches(points, candidate, coordinates)) {
          return new SegmentIndexes(candidate, candidate + coordinates.length - 1);
        }
      }
      for (int candidate = 0; candidate < preferredStart; candidate++) {
        if (matches(points, candidate, coordinates)) {
          return new SegmentIndexes(candidate, candidate + coordinates.length - 1);
        }
      }
    }
    int endIndex = coordinates.length == 0 ? fallbackStart : fallbackStart + coordinates.length - 1;
    return new SegmentIndexes(fallbackStart, Math.max(fallbackStart, endIndex));
  }

  private Coordinate[] effectiveCoordinates(SearchPathSegmentReadRecord record) {
    if (record.geometry() == null) {
      return new Coordinate[0];
    }
    Coordinate[] coordinates = record.geometry().getCoordinates();
    if (coordinates.length == 2
        && sameCoordinate(coordinates[0], coordinates[1])
        && Objects.equals(record.startedAt(), record.endedAt())) {
      return new Coordinate[] {coordinates[0]};
    }
    return coordinates;
  }

  private boolean matches(List<SearchPathPoint> points, int startIndex, Coordinate[] coordinates) {
    for (int i = 0; i < coordinates.length; i++) {
      if (!sameCoordinate(points.get(startIndex + i), coordinates[i])) {
        return false;
      }
    }
    return true;
  }

  private List<SearchPathPoint> pointsFrom(Geometry geometry, Instant startedAt) {
    if (geometry == null || geometry.getNumPoints() == 0) {
      return List.of();
    }
    List<SearchPathPoint> points = new ArrayList<>();
    Coordinate[] coordinates = geometry.getCoordinates();
    Instant base = startedAt == null ? Instant.EPOCH : startedAt;
    for (int i = 0; i < coordinates.length; i++) {
      Coordinate coordinate = coordinates[i];
      points.add(
          new SearchPathPoint(
              "db-point-%03d".formatted(i + 1),
              OffsetDateTime.ofInstant(base.plusSeconds(i * 5L), ZoneOffset.UTC),
              BigDecimal.valueOf(coordinate.x),
              BigDecimal.valueOf(coordinate.y),
              BigDecimal.ZERO,
              0));
    }
    return List.copyOf(points);
  }

  private Geometry lineStringOrNull(List<SearchPathPoint> points) {
    if (points.isEmpty()) {
      return null;
    }
    return lineString(points);
  }

  private LineString lineString(List<SearchPathPoint> points) {
    List<SearchPathPoint> sourcePoints =
        points.size() == 1 ? List.of(points.get(0), points.get(0)) : points;
    Coordinate[] coordinates =
        sourcePoints.stream()
            .map(point -> new Coordinate(point.lon().doubleValue(), point.lat().doubleValue()))
            .toArray(Coordinate[]::new);
    LineString lineString = GEOMETRY_FACTORY.createLineString(coordinates);
    lineString.setSRID(SRID);
    return lineString;
  }

  private LineString segmentLineString(List<SearchPathPoint> points, SearchPathSegment segment) {
    return lineString(points.subList(segment.startIndex(), segment.endIndex() + 1));
  }

  private Instant startedAt(SearchPathAggregate aggregate, Instant fallback) {
    return aggregate.points().stream()
        .map(SearchPathPoint::clientTs)
        .findFirst()
        .map(MyBatisSearchPathRepository::instant)
        .orElse(fallback);
  }

  private UUID uuidSegmentId(UUID pathId, SearchPathSegment segment) {
    try {
      return UUID.fromString(segment.id());
    } catch (IllegalArgumentException ignored) {
      String seed =
          "search-path-segment:%s:%d:%d"
              .formatted(pathId, segment.startIndex(), segment.endIndex());
      return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
  }

  private UUID excludedPointId(UUID pathId, PathExcludedPoint point) {
    String seed =
        "search-path-excluded-point:%s:%s:%s".formatted(pathId, point.pointId(), point.clientTs());
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private static Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  private static OffsetDateTime offsetDateTime(Instant value) {
    return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private static boolean sameCoordinate(SearchPathPoint point, Coordinate coordinate) {
    return Math.abs(point.lon().doubleValue() - coordinate.x) < 0.000000001
        && Math.abs(point.lat().doubleValue() - coordinate.y) < 0.000000001;
  }

  private static boolean sameCoordinate(Coordinate left, Coordinate right) {
    return Math.abs(left.x - right.x) < 0.000000001 && Math.abs(left.y - right.y) < 0.000000001;
  }

  private record SegmentIndexes(int start, int end) {}

  private record ResolvedDutyShift(UUID id, UUID accountId) {}
}
