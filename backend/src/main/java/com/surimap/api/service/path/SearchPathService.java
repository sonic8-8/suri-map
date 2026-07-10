package com.surimap.api.service.path;

import com.surimap.api.service.path.request.SearchPathPointServiceRequest;
import com.surimap.api.service.path.request.SearchPathPointsAppendServiceRequest;
import com.surimap.api.service.path.request.SearchPathQueryServiceRequest;
import com.surimap.api.service.path.request.SearchPathSegmentCorrectionServiceRequest;
import com.surimap.api.service.path.response.SearchPathExcludedPointServiceResponse;
import com.surimap.api.service.path.response.SearchPathPointsAppendServiceResponse;
import com.surimap.api.service.path.response.SearchPathQueryRowServiceResponse;
import com.surimap.api.service.path.response.SearchPathQuerySegmentServiceResponse;
import com.surimap.api.service.path.response.SearchPathQueryServiceResponse;
import com.surimap.api.service.path.response.SearchPathSegmentCorrectionServiceResponse;
import com.surimap.api.service.path.response.SearchPathSegmentServiceResponse;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathApiException;
import com.surimap.domain.path.SearchPathExcludedPoint;
import com.surimap.domain.path.SearchPathMapper;
import com.surimap.domain.path.SearchPathPoint;
import com.surimap.domain.path.SearchPathSegment;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.domain.path.validation.GpsPoint;
import com.surimap.domain.path.validation.GpsPointValidationResult.GpsPointExclusionReason;
import com.surimap.domain.path.validation.GpsPointValidator;
import com.surimap.global.event.SearchPathEventPublisher;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchPathService {

  private static final double VEHICLE_MIN_SPEED = 5.0;
  private static final double FOOT_MIN_SPEED = 0.5;
  private static final double FOOT_MAX_SPEED = 2.5;
  private static final int SRID = 4326;
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), SRID);

  private final SearchPathMapper searchPathMapper;
  private final SearchPathEventPublisher eventPublisher;
  private final GpsPointValidator gpsPointValidator;
  private final IdempotentResponseCache idempotentResponseCache;

  public SearchPathService(
      SearchPathMapper searchPathMapper,
      SearchPathEventPublisher eventPublisher,
      GpsPointValidator gpsPointValidator,
      IdempotentResponseCache idempotentResponseCache) {
    this.searchPathMapper = searchPathMapper;
    this.eventPublisher = eventPublisher;
    this.gpsPointValidator = gpsPointValidator;
    this.idempotentResponseCache = idempotentResponseCache;
  }

  public List<SearchPath> findAll() {
    return searchPathMapper.findAllPaths().stream().map(this::loadSearchPathDetails).toList();
  }

  public List<SearchPath> findByQuery(
      UUID incidentId, UUID opId, UUID policePhoneId, UUID accountId) {
    return searchPathMapper.findPaths(incidentId, opId, policePhoneId, accountId).stream()
        .map(this::loadSearchPathDetails)
        .toList();
  }

  private Optional<SearchPath> findById(UUID pathId) {
    return searchPathMapper.findPathById(pathId).map(this::loadSearchPathDetails);
  }

  private SearchPath save(SearchPath path) {
    Instant now = Instant.now();
    ResolvedDutyShift dutyShift = resolveDutyShift(path);
    Geometry geometry = lineStringOrNull(path.getPoints());
    Instant startedAt = startedAt(path, now);
    SearchPath persistedPath =
        path.toBuilder()
            .dutyShiftId(dutyShift.id())
            .accountId(dutyShift.accountId())
            .startedAt(startedAt)
            .geometry(geometry)
            .createdAt(path.getCreatedAt() == null ? startedAt : path.getCreatedAt())
            .updatedAt(now)
            .build();

    if (searchPathMapper.findPathById(path.getId()).isPresent()) {
      searchPathMapper.updatePath(persistedPath);
    } else {
      searchPathMapper.insertPath(persistedPath);
    }

    List<SearchPathSegment> persistedSegments = persistSegments(path, now);
    persistExcludedPoints(path, now);
    return persistedPath.toBuilder().segments(persistedSegments).build();
  }

  @Transactional
  public SearchPathPointsAppendServiceResponse appendPoints(
      SearchPathPointsAppendServiceRequest request) {
    requireIdempotencyKey(request.getIdempotencyKey());
    return idempotentResponseCache.replayOrRun(
        "POST /api/search-paths/batch",
        request.getIdempotencyKey(),
        request,
        200,
        SearchPathPointsAppendServiceResponse.class,
        () -> appendPointsOnce(request),
        this::metadataForPointsAppend);
  }

  private SearchPathPointsAppendServiceResponse appendPointsOnce(
      SearchPathPointsAppendServiceRequest request) {
    UUID accountId = request.getAccountId();
    if (accountId == null) {
      throw new SearchPathApiException("channel_not_allowed");
    }
    var validationResult =
        gpsPointValidator.validate(
            toValidatorPoints(request.getPoints()),
            request.getPoints().get(0).getClientTs().plusSeconds(20));

    List<SearchPathPoint> acceptedPoints = toAcceptedPoints(validationResult.getAcceptedPoints());
    List<SearchPathExcludedPoint> excludedPoints =
        toExcludedPoints(validationResult.getExcludedPoints());

    SearchPath path =
        findById(request.getPathId())
            .orElseGet(
                () ->
                    save(
                        SearchPath.builder()
                            .id(request.getPathId())
                            .incidentId(request.getIncidentId())
                            .opId(request.getOpId())
                            .policePhoneId(request.getPolicePhoneId())
                            .accountId(accountId)
                            .build()));
    if (accountId != null
        && path.getAccountId() != null
        && !accountId.equals(path.getAccountId())) {
      throw new SearchPathApiException("write_conflict");
    }
    if (path.getStatus() != SearchPathStatus.RECORDING) {
      throw new SearchPathApiException("write_conflict");
    }

    int pointOffset = path.getPoints().size();
    List<SearchPathSegment> segments = new ArrayList<>(path.getSegments());
    segments.addAll(autoSegments(acceptedPoints, pointOffset));

    path.appendAcceptedPoints(acceptedPoints);
    path.appendExcludedPoints(excludedPoints);
    path.replaceSegments(segments);
    path.bumpVersion();
    path = save(path);

    eventPublisher.publishPathAppended(path, request.getPolicePhoneId());

    return SearchPathPointsAppendServiceResponse.builder()
        .id(path.getId())
        .dutyShiftId(path.getDutyShiftId())
        .opId(path.getOpId())
        .policePhoneId(request.getPolicePhoneId())
        .accountId(path.getAccountId())
        .acceptedPointCount(validationResult.getAcceptedPoints().size())
        .excludedPointCount(validationResult.getExcludedPoints().size())
        .excludedPoints(
            path.getExcludedPoints().stream()
                .map(SearchPathExcludedPointServiceResponse::from)
                .toList())
        .geometry(toGeometry(path.getPoints()))
        .segments(path.getSegments().stream().map(SearchPathSegmentServiceResponse::from).toList())
        .version(path.getVersion())
        .status(path.getStatus())
        .build();
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new SearchPathApiException("write_conflict");
    }
  }

  private ResponseMetadata metadataForPointsAppend(SearchPathPointsAppendServiceResponse response) {
    return new ResponseMetadata(
        response.getId().toString(),
        response.getStatus().name(),
        response.getVersion(),
        response.getVersion());
  }

  public SearchPathQueryServiceResponse query(SearchPathQueryServiceRequest request) {
    List<SearchPathQueryRowServiceResponse> rows =
        findByQuery(
                request.getIncidentId(),
                request.getOpId(),
                request.getPolicePhoneId(),
                request.getAccountId())
            .stream()
            .sorted(Comparator.comparing(SearchPath::getVersion).reversed())
            .map(
                path ->
                    SearchPathQueryRowServiceResponse.builder()
                        .id(path.getId())
                        .incidentId(path.getIncidentId())
                        .opId(path.getOpId())
                        .dutyShiftId(path.getDutyShiftId())
                        .policePhoneId(path.getPolicePhoneId())
                        .accountId(path.getAccountId())
                        .status(path.getStatus())
                        .startedAt(path.getStartedAt())
                        .endedAt(path.getEndedAt())
                        .version(path.getVersion())
                        .geometry(toGeometry(path.getPoints()))
                        .segments(toQuerySegments(path.getPoints(), path.getSegments()))
                        .excludedPoints(
                            path.getExcludedPoints().stream()
                                .map(SearchPathExcludedPointServiceResponse::from)
                                .toList())
                        .build())
            .toList();
    return SearchPathQueryServiceResponse.builder().paths(rows).build();
  }

  @Transactional
  public SearchPathSegmentCorrectionServiceResponse correctSegment(
      SearchPathSegmentCorrectionServiceRequest request) {
    requireIdempotencyKey(request.getIdempotencyKey());
    return idempotentResponseCache.replayOrRun(
        "PATCH /api/search-path-segments/" + request.getSearchPathSegmentId(),
        request.getIdempotencyKey(),
        request,
        200,
        SearchPathSegmentCorrectionServiceResponse.class,
        () -> correctSegmentOnce(request),
        this::metadataForSegmentCorrection);
  }

  private SearchPathSegmentCorrectionServiceResponse correctSegmentOnce(
      SearchPathSegmentCorrectionServiceRequest request) {
    SearchPath owner =
        findAll().stream()
            .filter(
                path ->
                    path.getSegments().stream()
                        .anyMatch(
                            segment ->
                                segment.getId() != null
                                    && segment
                                        .getId()
                                        .toString()
                                        .equals(request.getSearchPathSegmentId())))
            .findFirst()
            .orElseThrow(() -> new SearchPathApiException("write_conflict"));

    SearchPathSegment corrected =
        owner.correctSegment(
            request.getSearchPathSegmentId(),
            request.getMovementType(),
            request.getCorrectedByAccountId(),
            OffsetDateTime.now());
    owner.bumpVersion();
    save(owner);

    eventPublisher.publishSegmentUpdated(owner, corrected);
    return SearchPathSegmentCorrectionServiceResponse.from(
        corrected, owner.getOpId(), owner.getPolicePhoneId());
  }

  private ResponseMetadata metadataForSegmentCorrection(
      SearchPathSegmentCorrectionServiceResponse response) {
    return new ResponseMetadata(
        response.getId(),
        response.getMovementType().name(),
        response.getVersion(),
        response.getVersion());
  }

  private List<GpsPoint> toValidatorPoints(List<SearchPathPointServiceRequest> points) {
    return points.stream()
        .map(
            p ->
                GpsPoint.builder()
                    .pointId(p.getPointId())
                    .clientTs(p.getClientTs())
                    .lon(p.getLon())
                    .lat(p.getLat())
                    .speedMps(p.getSpeedMps())
                    .horizontalAccuracyM(p.getHorizontalAccuracyM())
                    .build())
        .toList();
  }

  private List<SearchPathPoint> toAcceptedPoints(List<GpsPoint> points) {
    return points.stream()
        .map(
            p ->
                SearchPathPoint.builder()
                    .pointId(p.getPointId())
                    .clientTs(p.getClientTs())
                    .lon(p.getLon())
                    .lat(p.getLat())
                    .speedMps(p.getSpeedMps())
                    .horizontalAccuracyM(p.getHorizontalAccuracyM())
                    .build())
        .toList();
  }

  private List<SearchPathExcludedPoint> toExcludedPoints(
      List<com.surimap.domain.path.validation.GpsPointValidationResult.ExcludedPoint> points) {
    return points.stream()
        .map(
            p ->
                SearchPathExcludedPoint.builder()
                    .pointId(p.getPoint().getPointId())
                    .reason(qualityReason(p.getReason()))
                    .clientTs(p.getPoint().getClientTs())
                    .build())
        .toList();
  }

  private String qualityReason(GpsPointExclusionReason reason) {
    return switch (reason) {
      case LOW_ACCURACY -> "low_accuracy";
      case CLOCK_SKEW -> "clock_skew";
      case INVALID_SPEED -> "invalid_speed";
      case DISTANCE_JUMP -> "distance_jump";
    };
  }

  private List<SearchPathSegment> autoSegments(List<SearchPathPoint> points) {
    return autoSegments(points, 0);
  }

  private List<SearchPathSegment> autoSegments(List<SearchPathPoint> points, int pointOffset) {
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
        segments.add(segment(points, start, i - 1, current, pointOffset));
        start = i;
        current = next;
      }
    }
    segments.add(segment(points, start, perPoint.size() - 1, current, pointOffset));
    return segments;
  }

  private SearchPathSegment segment(
      List<SearchPathPoint> points, int start, int end, MovementType type, int pointOffset) {
    return SearchPathSegment.builder()
        .movementType(type)
        .movementTypeSource(MovementTypeSource.AUTO)
        .startIndex(pointOffset + start)
        .endIndex(pointOffset + end)
        .startPointId(points.get(start).getPointId())
        .endPointId(points.get(end).getPointId())
        .build();
  }

  private MovementType classify(SearchPathPoint point) {
    double speed = point.getSpeedMps().doubleValue();
    if (speed >= VEHICLE_MIN_SPEED) {
      return MovementType.VEHICLE;
    }
    if (speed >= FOOT_MIN_SPEED && speed <= FOOT_MAX_SPEED) {
      return MovementType.FOOT;
    }
    return MovementType.UNKNOWN;
  }

  private List<List<Double>> toGeometry(List<SearchPathPoint> points) {
    return points.stream()
        .map(p -> List.of(p.getLon().doubleValue(), p.getLat().doubleValue()))
        .toList();
  }

  private List<SearchPathQuerySegmentServiceResponse> toQuerySegments(
      List<SearchPathPoint> points, List<SearchPathSegment> segments) {
    return segments.stream()
        .filter(segment -> hasValidPointRange(points, segment))
        .map(segment -> toQuerySegment(points, segment))
        .toList();
  }

  private SearchPathQuerySegmentServiceResponse toQuerySegment(
      List<SearchPathPoint> points, SearchPathSegment segment) {
    List<SearchPathPoint> segmentPoints =
        points.subList(segment.getStartIndex(), segment.getEndIndex() + 1);
    return SearchPathQuerySegmentServiceResponse.builder()
        .id(segment.getId().toString())
        .version(segment.getVersion())
        .movementType(segment.getMovementType())
        .movementTypeSource(segment.getMovementTypeSource())
        .geometry(toGeometry(segmentPoints))
        .startedAt(segmentPoints.get(0).getClientTs())
        .endedAt(segmentPoints.get(segmentPoints.size() - 1).getClientTs())
        .correctedByAccountId(segment.getCorrectedByAccountId())
        .correctedAt(segment.getCorrectedAt())
        .build();
  }

  private boolean hasValidPointRange(List<SearchPathPoint> points, SearchPathSegment segment) {
    return segment.getStartIndex() >= 0
        && segment.getEndIndex() >= segment.getStartIndex()
        && segment.getEndIndex() < points.size();
  }

  private List<SearchPathSegment> persistSegments(SearchPath path, Instant now) {
    searchPathMapper.deleteSegments(path.getId());
    List<SearchPathPoint> points = path.getPoints();
    List<SearchPathSegment> persistedSegments = new ArrayList<>();
    for (SearchPathSegment segment : path.getSegments()) {
      UUID segmentId = uuidSegmentId(path.getId(), segment);
      LineString geometry = segmentLineString(points, segment);
      Instant startedAt = instant(points.get(segment.getStartIndex()).getClientTs());
      Instant endedAt = instant(points.get(segment.getEndIndex()).getClientTs());
      SearchPathSegment persistedSegment =
          segment.toBuilder()
              .id(segmentId)
              .searchPathId(path.getId())
              .geometry(geometry)
              .startedAt(startedAt)
              .endedAt(endedAt)
              .createdAt(segment.getCreatedAt() == null ? now : segment.getCreatedAt())
              .updatedAt(now)
              .build();
      searchPathMapper.insertSegment(persistedSegment);
      persistedSegments.add(persistedSegment);
    }
    return List.copyOf(persistedSegments);
  }

  private SearchPath loadSearchPathDetails(SearchPath record) {
    List<SearchPathPoint> points = pointsFrom(record.getGeometry(), record.getStartedAt());
    List<SearchPathSegment> segments =
        segmentsFrom(searchPathMapper.findSegmentsByPathId(record.getId()), points);
    List<SearchPathExcludedPoint> excludedPoints = excludedPointsFrom(record.getId());
    return record.toBuilder()
        .points(points)
        .excludedPoints(excludedPoints)
        .segments(segments)
        .build();
  }

  private ResolvedDutyShift resolveDutyShift(SearchPath path) {
    UUID accountId = path.getAccountId();
    if (accountId != null) {
      UUID dutyShiftId =
          searchPathMapper
              .findActiveDutyShiftIdByAccount(path.getOpId(), accountId)
              .orElseThrow(() -> new SearchPathApiException("police_phone_not_assigned"));
      return new ResolvedDutyShift(dutyShiftId, accountId);
    }
    UUID dutyShiftId =
        searchPathMapper
            .findActiveDutyShiftId(path.getOpId(), path.getPolicePhoneId())
            .orElseThrow(() -> new SearchPathApiException("police_phone_not_assigned"));
    UUID inferredAccountId =
        searchPathMapper
            .findActiveDutyShiftAccountId(path.getOpId(), path.getPolicePhoneId())
            .orElseThrow(() -> new SearchPathApiException("police_phone_not_assigned"));
    return new ResolvedDutyShift(dutyShiftId, inferredAccountId);
  }

  private void persistExcludedPoints(SearchPath path, Instant now) {
    searchPathMapper.deleteExcludedPoints(path.getId());
    for (SearchPathExcludedPoint point : path.getExcludedPoints()) {
      SearchPathExcludedPoint persistedPoint =
          point.toBuilder()
              .id(point.getId() == null ? excludedPointId(path.getId(), point) : point.getId())
              .searchPathId(path.getId())
              .createdAt(point.getCreatedAt() == null ? now : point.getCreatedAt())
              .updatedAt(now)
              .build();
      searchPathMapper.insertExcludedPoint(persistedPoint);
    }
  }

  private List<SearchPathExcludedPoint> excludedPointsFrom(UUID pathId) {
    return searchPathMapper.findExcludedPointsByPathId(pathId);
  }

  private List<SearchPathSegment> segmentsFrom(
      List<SearchPathSegment> segments, List<SearchPathPoint> points) {
    int startIndex = 0;
    for (SearchPathSegment segment : segments) {
      SegmentIndexes indexes = segmentIndexes(segment, points, startIndex);
      segment.assignPointRange(
          indexes.start(),
          indexes.end(),
          "db-point-%03d".formatted(indexes.start() + 1),
          "db-point-%03d".formatted(indexes.end() + 1));
      startIndex = indexes.end() + 1;
    }
    return segments.stream()
        .sorted(Comparator.comparingInt(SearchPathSegment::getStartIndex))
        .toList();
  }

  private SegmentIndexes segmentIndexes(
      SearchPathSegment segment, List<SearchPathPoint> points, int fallbackStart) {
    Coordinate[] coordinates = effectiveCoordinates(segment);
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

  private Coordinate[] effectiveCoordinates(SearchPathSegment segment) {
    if (segment.getGeometry() == null) {
      return new Coordinate[0];
    }
    Coordinate[] coordinates = segment.getGeometry().getCoordinates();
    if (coordinates.length == 2
        && sameCoordinate(coordinates[0], coordinates[1])
        && Objects.equals(segment.getStartedAt(), segment.getEndedAt())) {
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
          SearchPathPoint.builder()
              .pointId("db-point-%03d".formatted(i + 1))
              .clientTs(OffsetDateTime.ofInstant(base.plusSeconds(i * 5L), ZoneOffset.UTC))
              .lon(BigDecimal.valueOf(coordinate.x))
              .lat(BigDecimal.valueOf(coordinate.y))
              .speedMps(BigDecimal.ZERO)
              .horizontalAccuracyM(0)
              .build());
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
            .map(
                point -> new Coordinate(point.getLon().doubleValue(), point.getLat().doubleValue()))
            .toArray(Coordinate[]::new);
    LineString lineString = GEOMETRY_FACTORY.createLineString(coordinates);
    lineString.setSRID(SRID);
    return lineString;
  }

  private LineString segmentLineString(List<SearchPathPoint> points, SearchPathSegment segment) {
    return lineString(points.subList(segment.getStartIndex(), segment.getEndIndex() + 1));
  }

  private Instant startedAt(SearchPath path, Instant fallback) {
    return path.getPoints().stream()
        .map(SearchPathPoint::getClientTs)
        .findFirst()
        .map(SearchPathService::instant)
        .orElse(fallback);
  }

  private UUID uuidSegmentId(UUID pathId, SearchPathSegment segment) {
    if (segment.getId() != null) {
      return segment.getId();
    }
    String seed =
        "search-path-segment:%s:%d:%d"
            .formatted(pathId, segment.getStartIndex(), segment.getEndIndex());
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private UUID excludedPointId(UUID pathId, SearchPathExcludedPoint point) {
    String seed =
        "search-path-excluded-point:%s:%s:%s"
            .formatted(pathId, point.getPointId(), point.getClientTs());
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private static Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  private static boolean sameCoordinate(SearchPathPoint point, Coordinate coordinate) {
    return Double.compare(point.getLon().doubleValue(), coordinate.x) == 0
        && Double.compare(point.getLat().doubleValue(), coordinate.y) == 0;
  }

  private static boolean sameCoordinate(Coordinate first, Coordinate second) {
    return Double.compare(first.x, second.x) == 0 && Double.compare(first.y, second.y) == 0;
  }

  private record ResolvedDutyShift(UUID id, UUID accountId) {}

  private record SegmentIndexes(int start, int end) {}
}
