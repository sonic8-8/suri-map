package com.surimap.domain.marker;

import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerSupportRequestType;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.query.MarkerPhotoSummary;
import com.surimap.marker.query.MarkerView;
import com.surimap.marker.seed.SeedMarker;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Marker {

  private static final int MAX_MEMO_LENGTH = 2000;

  private UUID id;
  private UUID incidentId;
  private UUID operationalPeriodId;
  private UUID dutyShiftId;
  private String markerType;
  private String supportRequestType;
  private Point location;
  private String memo;
  private Instant occurredAt;
  private UUID createdByAccountId;
  private UUID policePhoneId;
  private String markerSource;
  private String status;
  private long version;

  @Builder
  private Marker(
      UUID id,
      UUID incidentId,
      UUID operationalPeriodId,
      UUID dutyShiftId,
      MarkerType markerType,
      MarkerSupportRequestType supportRequestType,
      Point location,
      String memo,
      Instant occurredAt,
      UUID createdByAccountId,
      UUID policePhoneId,
      MarkerSource markerSource,
      MarkerStatus status,
      long version) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.incidentId = Objects.requireNonNull(incidentId, "incidentId must not be null");
    this.operationalPeriodId =
        Objects.requireNonNull(operationalPeriodId, "operationalPeriodId must not be null");
    this.dutyShiftId = dutyShiftId;
    this.markerType = Objects.requireNonNull(markerType, "markerType must not be null").name();
    this.supportRequestType = supportRequestType == null ? null : supportRequestType.name();
    this.location = Objects.requireNonNull(location, "location must not be null");
    this.memo = memo;
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    this.createdByAccountId =
        Objects.requireNonNull(createdByAccountId, "createdByAccountId must not be null");
    this.markerSource =
        Objects.requireNonNull(markerSource, "markerSource must not be null").name();
    if (markerSource == MarkerSource.APP) {
      Objects.requireNonNull(policePhoneId, "policePhoneId must not be null");
    }
    this.policePhoneId = policePhoneId;
    this.status = Objects.requireNonNull(status, "status must not be null").name();
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive");
    }
    this.version = version;
  }

  public static Marker fromSeed(UUID incidentId, SeedMarker seed) {
    return builder()
        .id(seed.id())
        .incidentId(incidentId)
        .operationalPeriodId(seed.operationalPeriodId())
        .dutyShiftId(seed.dutyShiftId())
        .markerType(seed.markerType())
        .supportRequestType(seed.supportRequestType())
        .location(seed.location())
        .memo(seed.memo())
        .occurredAt(seed.occurredAt())
        .createdByAccountId(seed.createdByAccountId())
        .policePhoneId(seed.policePhoneId())
        .markerSource(MarkerSource.MOCK_SEED)
        .status(MarkerStatus.ACTIVE)
        .version(1L)
        .build();
  }

  public MarkerView toView(List<MarkerPhotoSummary> photos) {
    return new MarkerView(
        id,
        incidentId,
        operationalPeriodId,
        dutyShiftId,
        createdByAccountId,
        policePhoneId,
        MarkerType.valueOf(markerType),
        supportRequestType == null ? null : MarkerSupportRequestType.valueOf(supportRequestType),
        MarkerSource.valueOf(markerSource),
        MarkerStatus.valueOf(status),
        version,
        location,
        memo,
        occurredAt,
        photos);
  }

  public boolean isFieldMarkerCreatedBy(UUID accountId) {
    return MarkerSource.APP.name().equals(markerSource) && createdByAccountId.equals(accountId);
  }

  public boolean isReferenceMarker() {
    return MarkerSource.MOCK_SEED.name().equals(markerSource)
        || MarkerSource.SYSTEM.name().equals(markerSource);
  }

  public void update(
      long expectedVersion, String requestedType, Point requestedLocation, String requestedMemo) {
    validateType(requestedType);
    if (requestedMemo != null && requestedMemo.length() > MAX_MEMO_LENGTH) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
    markUpdated(expectedVersion);
    if (requestedType != null && !requestedType.isBlank()) {
      markerType = requestedType;
    }
    if (requestedLocation != null) {
      location = requestedLocation;
    }
    if (requestedMemo != null) {
      memo = requestedMemo;
    }
  }

  public void markUpdated(long expectedVersion) {
    requireVersion(expectedVersion);
    status = MarkerStatus.UPDATED.name();
    version++;
  }

  public void delete(long expectedVersion) {
    requireVersion(expectedVersion);
    status = MarkerStatus.DELETED.name();
    version++;
  }

  public void requireVersion(long expectedVersion) {
    if (MarkerStatus.DELETED.name().equals(status) || version != expectedVersion) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  public static void validateType(String requestedType) {
    if (requestedType == null || requestedType.isBlank()) {
      return;
    }
    try {
      MarkerType.valueOf(requestedType);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
  }

  public static void validateLocation(Point location) {
    // 수색구역 밖의 단서·발견도 기록할 수 있으므로 좌표 자체만 검사한다.
    if (location == null || location.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_GEOMETRY, "location is null or empty");
    }
    if (location.getSRID() != 4326) {
      throw new BusinessException(ErrorCode.INVALID_GEOMETRY, "location SRID must be 4326");
    }
    Coordinate coordinate = location.getCoordinate();
    if (coordinate == null || !Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)) {
      throw new BusinessException(ErrorCode.INVALID_GEOMETRY, "coordinates must be finite numbers");
    }
    if (coordinate.x < -180.0 || coordinate.x > 180.0) {
      throw new BusinessException(
          ErrorCode.INVALID_GEOMETRY, "longitude out of range: " + coordinate.x);
    }
    if (coordinate.y < -90.0 || coordinate.y > 90.0) {
      throw new BusinessException(
          ErrorCode.INVALID_GEOMETRY, "latitude out of range: " + coordinate.y);
    }
  }
}
