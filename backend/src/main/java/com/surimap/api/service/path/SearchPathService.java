package com.surimap.api.service.path;

import com.surimap.api.controller.path.request.PathBatchAppendRequest;
import com.surimap.api.controller.path.request.PathBatchPointRequest;
import com.surimap.api.controller.path.response.PathBatchAppendResponse;
import com.surimap.api.controller.path.response.PathQueryResponse;
import com.surimap.api.controller.path.response.PathQueryRow;
import com.surimap.api.controller.path.response.PathQuerySegmentRow;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import com.surimap.domain.path.PathExcludedPoint;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathApiException;
import com.surimap.domain.path.SearchPathExcludedPointPersistenceRecord;
import com.surimap.domain.path.SearchPathExcludedPointReadRecord;
import com.surimap.domain.path.SearchPathMapper;
import com.surimap.domain.path.SearchPathPersistenceRecord;
import com.surimap.domain.path.SearchPathPoint;
import com.surimap.domain.path.SearchPathReadRecord;
import com.surimap.domain.path.SearchPathSegment;
import com.surimap.domain.path.SearchPathSegmentPersistenceRecord;
import com.surimap.domain.path.SearchPathSegmentReadRecord;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.domain.path.validation.GpsPathPoint;
import com.surimap.domain.path.validation.GpsPathValidationResult.QualityReason;
import com.surimap.domain.path.validation.GpsPathValidator;
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
import org.springframework.stereotype.Service;

@Service
public class SearchPathService {

  private static final double VEHICLE_MIN_SPEED = 5.0;
  private static final double FOOT_MIN_SPEED = 0.5;
  private static final double FOOT_MAX_SPEED = 2.5;
  private static final int SRID = 4326;
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), SRID);

  private final SearchPathMapper searchPathMapper;
  private final PathEventPublisher eventPublisher;
  private final GpsPathValidator gpsPathValidator;

  public SearchPathService(
      SearchPathMapper searchPathMapper,
      PathEventPublisher eventPublisher,
      GpsPathValidator gpsPathValidator) {
    this.searchPathMapper = searchPathMapper;
    this.eventPublisher = eventPublisher;
    this.gpsPathValidator = gpsPathValidator;
  }

  public List<SearchPath> findAll() {
    return searchPathMapper.findAllPaths().stream().map(this::toAggregate).toList();
  }

  public List<SearchPath> findByQuery(
      UUID incidentId, UUID opId, UUID policePhoneId, UUID accountId) {
    return searchPathMapper.findPaths(incidentId, opId, policePhoneId, accountId).stream()
        .map(this::toAggregate)
        .toList();
  }

  private Optional<SearchPath> findById(UUID pathId) {
    return searchPathMapper.findPathById(pathId).map(this::toAggregate);
  }

  private SearchPath save(SearchPath aggregate) {
    Instant now = Instant.now();
    ResolvedDutyShift dutyShift = resolveDutyShift(aggregate);
    Geometry geometry = lineStringOrNull(aggregate.getPoints());
    Instant startedAt = startedAt(aggregate, now);
    SearchPathPersistenceRecord record =
        new SearchPathPersistenceRecord(
            aggregate.getId(),
            dutyShift.id(),
            dutyShift.accountId(),
            aggregate.getStatus().name(),
            startedAt,
            aggregate.getEndedAt(),
            geometry,
            aggregate.getVersion(),
            startedAt,
            now);

    if (searchPathMapper.findPathById(aggregate.getId()).isPresent()) {
      searchPathMapper.updatePath(record);
    } else {
      searchPathMapper.insertPath(record);
    }

    List<SearchPathSegment> persistedSegments = persistSegments(aggregate, now);
    persistExcludedPoints(aggregate, now);
    return SearchPath.builder()
        .id(aggregate.getId())
        .dutyShiftId(dutyShift.id())
        .incidentId(aggregate.getIncidentId())
        .opId(aggregate.getOpId())
        .policePhoneId(aggregate.getPolicePhoneId())
        .accountId(dutyShift.accountId())
        .status(aggregate.getStatus())
        .version(aggregate.getVersion())
        .points(aggregate.getPoints())
        .excludedPoints(aggregate.getExcludedPoints())
        .segments(persistedSegments)
        .build();
  }

  public PathBatchAppendResponse appendBatch(
      PathBatchAppendRequest request, UUID policePhoneId, UUID accountId) {
    if (accountId == null) {
      throw new SearchPathApiException("channel_not_allowed");
    }
    var validationResult =
        gpsPathValidator.validateBatch(
            toValidatorPoints(request.points()),
            request.points().get(0).clientTs().plusSeconds(20));

    List<SearchPathPoint> acceptedPoints = toAcceptedPoints(validationResult.acceptedPoints());
    List<PathExcludedPoint> excludedPoints = toExcludedPoints(validationResult.excludedPoints());

    SearchPath aggregate =
        findById(request.pathId())
            .orElseGet(
                () ->
                    save(
                        SearchPath.builder()
                            .id(request.pathId())
                            .incidentId(request.incidentId())
                            .opId(request.opId())
                            .policePhoneId(policePhoneId)
                            .accountId(accountId)
                            .build()));
    if (accountId != null
        && aggregate.getAccountId() != null
        && !accountId.equals(aggregate.getAccountId())) {
      throw new SearchPathApiException("write_conflict");
    }
    if (aggregate.getStatus() != SearchPathStatus.RECORDING) {
      throw new SearchPathApiException("write_conflict");
    }

    int pointOffset = aggregate.getPoints().size();
    int segmentOffset = aggregate.getSegments().size();
    List<SearchPathSegment> segments = new ArrayList<>(aggregate.getSegments());
    segments.addAll(autoSegments(acceptedPoints, pointOffset, segmentOffset));

    aggregate.appendAcceptedPoints(acceptedPoints);
    aggregate.appendExcludedPoints(excludedPoints);
    aggregate.replaceSegments(segments);
    aggregate.bumpVersion();
    aggregate = save(aggregate);

    eventPublisher.publishPathAppended(
        new PathAppendedPublishRequest(
            aggregate.getId(),
            aggregate.getIncidentId(),
            aggregate.getStatus(),
            aggregate.getVersion(),
            aggregate.getOpId(),
            policePhoneId,
            aggregate.getAccountId()));

    return new PathBatchAppendResponse(
        aggregate.getId(),
        aggregate.getDutyShiftId(),
        aggregate.getOpId(),
        policePhoneId,
        aggregate.getAccountId(),
        validationResult.acceptedPoints().size(),
        validationResult.excludedPoints().size(),
        aggregate.getExcludedPoints(),
        toGeometry(aggregate.getPoints()),
        aggregate.getSegments(),
        aggregate.getVersion(),
        aggregate.getStatus());
  }

  public PathQueryResponse query(UUID incidentId, UUID opId, UUID policePhoneId) {
    return query(incidentId, opId, policePhoneId, null);
  }

  public PathQueryResponse query(UUID incidentId, UUID opId, UUID policePhoneId, UUID accountId) {
    List<PathQueryRow> rows =
        findByQuery(incidentId, opId, policePhoneId, accountId).stream()
            .sorted(Comparator.comparing(SearchPath::getVersion).reversed())
            .map(
                path ->
                    new PathQueryRow(
                        path.getId(),
                        path.getIncidentId(),
                        path.getOpId(),
                        path.getDutyShiftId(),
                        path.getPolicePhoneId(),
                        path.getAccountId(),
                        path.getStatus(),
                        path.getStartedAt(),
                        path.getEndedAt(),
                        path.getVersion(),
                        toGeometry(path.getPoints()),
                        toQuerySegments(path.getPoints(), path.getSegments()),
                        path.getExcludedPoints()))
            .toList();
    return new PathQueryResponse(rows);
  }

  public SegmentCorrectionResult correctSegment(
      String segmentId, MovementType movementType, UUID correctedByAccountId) {
    SearchPath owner =
        findAll().stream()
            .filter(
                path ->
                    path.getSegments().stream().anyMatch(segment -> segment.id().equals(segmentId)))
            .findFirst()
            .orElseThrow(() -> new SearchPathApiException("write_conflict"));

    SearchPathSegment corrected =
        owner.correctSegment(segmentId, movementType, correctedByAccountId, OffsetDateTime.now());
    owner.bumpVersion();
    save(owner);

    eventPublisher.publishSegmentUpdated(
        new SearchPathSegmentUpdatedPublishRequest(
            owner.getId(),
            owner.getIncidentId(),
            owner.getStatus(),
            owner.getVersion(),
            owner.getOpId(),
            owner.getPolicePhoneId(),
            owner.getAccountId(),
            corrected.id(),
            corrected.movementType(),
            corrected.movementTypeSource()));
    return new SegmentCorrectionResult(corrected, owner.getOpId(), owner.getPolicePhoneId());
  }

  private List<GpsPathPoint> toValidatorPoints(List<PathBatchPointRequest> points) {
    return points.stream()
        .map(
            p ->
                new GpsPathPoint(
                    p.pointId(),
                    p.clientTs(),
                    p.lon(),
                    p.lat(),
                    p.speedMps(),
                    p.horizontalAccuracyM()))
        .toList();
  }

  private List<SearchPathPoint> toAcceptedPoints(List<GpsPathPoint> points) {
    return points.stream()
        .map(
            p ->
                new SearchPathPoint(
                    p.pointId(),
                    p.clientTs(),
                    p.lon(),
                    p.lat(),
                    p.speedMps(),
                    p.horizontalAccuracyM()))
        .toList();
  }

  private List<PathExcludedPoint> toExcludedPoints(
      List<com.surimap.domain.path.validation.GpsPathValidationResult.ExcludedPoint> points) {
    return points.stream()
        .map(
            p ->
                new PathExcludedPoint(
                    p.point().pointId(), qualityReason(p.reason()), p.point().clientTs()))
        .toList();
  }

  private String qualityReason(QualityReason reason) {
    return switch (reason) {
      case LOW_ACCURACY -> "low_accuracy";
      case CLOCK_SKEW -> "clock_skew";
      case INVALID_SPEED -> "invalid_speed";
      case DISTANCE_JUMP -> "distance_jump";
    };
  }

  private List<SearchPathSegment> autoSegments(List<SearchPathPoint> points) {
    return autoSegments(points, 0, 0);
  }

  private List<SearchPathSegment> autoSegments(
      List<SearchPathPoint> points, int pointOffset, int segmentOffset) {
    List<SearchPathSegment> segments = new ArrayList<>();
    if (points.isEmpty()) {
      return segments;
    }
    List<MovementType> perPoint = new ArrayList<>();
    for (SearchPathPoint point : points) {
      perPoint.add(classify(point));
    }

    int runStart = 0;
    while (runStart < perPoint.size()) {
      MovementType runType = perPoint.get(runStart);
      int runEnd = runStart;
      while (runEnd + 1 < perPoint.size() && perPoint.get(runEnd + 1) == runType) {
        runEnd++;
      }
      if ((runType == MovementType.VEHICLE || runType == MovementType.FOOT)
          && (runEnd - runStart + 1) < 3) {
        for (int i = runStart; i <= runEnd; i++) {
          perPoint.set(i, MovementType.UNKNOWN);
        }
      }
      runStart = runEnd + 1;
    }

    int start = 0;
    MovementType current = perPoint.get(0);
    for (int i = 1; i < perPoint.size(); i++) {
      MovementType next = perPoint.get(i);
      if (next != current) {
        segments.add(
            segment(points, start, i - 1, current, pointOffset, segmentOffset + segments.size()));
        start = i;
        current = next;
      }
    }
    segments.add(
        segment(
            points,
            start,
            perPoint.size() - 1,
            current,
            pointOffset,
            segmentOffset + segments.size()));
    return segments;
  }

  private SearchPathSegment segment(
      List<SearchPathPoint> points,
      int start,
      int end,
      MovementType type,
      int pointOffset,
      int segmentIndex) {
    return new SearchPathSegment(
        "seg-%03d".formatted(segmentIndex + 1),
        1L,
        type,
        MovementTypeSource.AUTO,
        pointOffset + start,
        pointOffset + end,
        points.get(start).pointId(),
        points.get(end).pointId(),
        null,
        null);
  }

  private MovementType classify(SearchPathPoint point) {
    double speed = point.speedMps().doubleValue();
    if (speed >= VEHICLE_MIN_SPEED) {
      return MovementType.VEHICLE;
    }
    if (speed >= FOOT_MIN_SPEED && speed <= FOOT_MAX_SPEED) {
      return MovementType.FOOT;
    }
    return MovementType.UNKNOWN;
  }

  private List<List<Double>> toGeometry(List<SearchPathPoint> points) {
    return points.stream().map(p -> List.of(p.lon().doubleValue(), p.lat().doubleValue())).toList();
  }

  private List<PathQuerySegmentRow> toQuerySegments(
      List<SearchPathPoint> points, List<SearchPathSegment> segments) {
    return segments.stream()
        .filter(segment -> hasValidPointRange(points, segment))
        .map(segment -> toQuerySegment(points, segment))
        .toList();
  }

  private PathQuerySegmentRow toQuerySegment(
      List<SearchPathPoint> points, SearchPathSegment segment) {
    List<SearchPathPoint> segmentPoints =
        points.subList(segment.startIndex(), segment.endIndex() + 1);
    return new PathQuerySegmentRow(
        segment.id(),
        segment.version(),
        segment.movementType(),
        segment.movementTypeSource(),
        toGeometry(segmentPoints),
        segmentPoints.get(0).clientTs(),
        segmentPoints.get(segmentPoints.size() - 1).clientTs(),
        segment.correctedByAccountId(),
        segment.correctedAt());
  }

  private boolean hasValidPointRange(List<SearchPathPoint> points, SearchPathSegment segment) {
    return segment.startIndex() >= 0
        && segment.endIndex() >= segment.startIndex()
        && segment.endIndex() < points.size();
  }

  private List<SearchPathSegment> persistSegments(SearchPath aggregate, Instant now) {
    searchPathMapper.deleteSegments(aggregate.getId());
    List<SearchPathPoint> points = aggregate.getPoints();
    List<SearchPathSegment> persistedSegments = new ArrayList<>();
    for (SearchPathSegment segment : aggregate.getSegments()) {
      UUID segmentId = uuidSegmentId(aggregate.getId(), segment);
      LineString geometry = segmentLineString(points, segment);
      Instant startedAt = instant(points.get(segment.startIndex()).clientTs());
      Instant endedAt = instant(points.get(segment.endIndex()).clientTs());
      searchPathMapper.insertSegment(
          new SearchPathSegmentPersistenceRecord(
              segmentId,
              aggregate.getId(),
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

  private SearchPath toAggregate(SearchPathReadRecord record) {
    List<SearchPathPoint> points = pointsFrom(record.geometry(), record.startedAt());
    List<SearchPathSegmentReadRecord> segmentRecords =
        searchPathMapper.findSegmentsByPathId(record.id());
    List<SearchPathSegment> segments = segmentsFrom(segmentRecords, points);
    List<PathExcludedPoint> excludedPoints = excludedPointsFrom(record.id());
    return SearchPath.builder()
        .id(record.id())
        .dutyShiftId(record.dutyShiftId())
        .incidentId(record.incidentId())
        .opId(record.opId())
        .policePhoneId(record.policePhoneId())
        .accountId(record.accountId())
        .startedAt(record.startedAt())
        .endedAt(record.endedAt())
        .status(SearchPathStatus.valueOf(record.status()))
        .version(record.version())
        .points(points)
        .excludedPoints(excludedPoints)
        .segments(segments)
        .build();
  }

  private ResolvedDutyShift resolveDutyShift(SearchPath aggregate) {
    UUID accountId = aggregate.getAccountId();
    if (accountId != null) {
      UUID dutyShiftId =
          searchPathMapper
              .findActiveDutyShiftIdByAccount(aggregate.getOpId(), accountId)
              .orElseThrow(() -> new SearchPathApiException("police_phone_not_assigned"));
      return new ResolvedDutyShift(dutyShiftId, accountId);
    }
    UUID dutyShiftId =
        searchPathMapper
            .findActiveDutyShiftId(aggregate.getOpId(), aggregate.getPolicePhoneId())
            .orElseThrow(() -> new SearchPathApiException("police_phone_not_assigned"));
    UUID inferredAccountId =
        searchPathMapper
            .findActiveDutyShiftAccountId(aggregate.getOpId(), aggregate.getPolicePhoneId())
            .orElseThrow(() -> new SearchPathApiException("police_phone_not_assigned"));
    return new ResolvedDutyShift(dutyShiftId, inferredAccountId);
  }

  private void persistExcludedPoints(SearchPath aggregate, Instant now) {
    searchPathMapper.deleteExcludedPoints(aggregate.getId());
    for (PathExcludedPoint point : aggregate.getExcludedPoints()) {
      searchPathMapper.insertExcludedPoint(
          new SearchPathExcludedPointPersistenceRecord(
              excludedPointId(aggregate.getId(), point),
              aggregate.getId(),
              point.pointId(),
              point.reason(),
              instant(point.clientTs()),
              now,
              now));
    }
  }

  private List<PathExcludedPoint> excludedPointsFrom(UUID pathId) {
    return searchPathMapper.findExcludedPointsByPathId(pathId).stream()
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
    return segments.stream().sorted(Comparator.comparingInt(SearchPathSegment::startIndex)).toList();
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

  private Instant startedAt(SearchPath aggregate, Instant fallback) {
    return aggregate.getPoints().stream()
        .map(SearchPathPoint::clientTs)
        .findFirst()
        .map(SearchPathService::instant)
        .orElse(fallback);
  }

  private UUID uuidSegmentId(UUID pathId, SearchPathSegment segment) {
    try {
      return UUID.fromString(segment.id());
    } catch (IllegalArgumentException ignored) {
      String seed =
          "search-path-segment:%s:%d:%d".formatted(pathId, segment.startIndex(), segment.endIndex());
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
    return Double.compare(point.lon().doubleValue(), coordinate.x) == 0
        && Double.compare(point.lat().doubleValue(), coordinate.y) == 0;
  }

  private static boolean sameCoordinate(Coordinate first, Coordinate second) {
    return Double.compare(first.x, second.x) == 0 && Double.compare(first.y, second.y) == 0;
  }

  private record ResolvedDutyShift(UUID id, UUID accountId) {}

  private record SegmentIndexes(int start, int end) {}
}
