package com.surimap.domain.path;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathSegment {

  // 자동 구간을 만들거나 늘릴 때 넘지 않는 좌표 수다. 한 묶음에 올릴 수 있는 최대 좌표 수와 같다.
  public static final int MAX_AUTO_POINT_COUNT = 120;

  private UUID id;
  private UUID searchPathId;
  private MovementType movementType;
  private MovementTypeSource movementTypeSource;
  private Geometry geometry;
  private Instant startedAt;
  private Instant endedAt;
  private UUID correctedByAccountId;
  private OffsetDateTime correctedAt;
  private long version = 1L;
  // 기존 구간은 전환 시점의 경로 버전, 이후에는 실제 추가·보정이 확정된 경로 버전이다.
  private Long lastChangedPathVersion;
  private Instant createdAt;
  private Instant updatedAt;
  private int startIndex = -1;
  private int endIndex = -1;
  private String startPointId;
  private String endPointId;

  @Builder(toBuilder = true)
  private SearchPathSegment(
      UUID id,
      UUID searchPathId,
      MovementType movementType,
      MovementTypeSource movementTypeSource,
      Geometry geometry,
      Instant startedAt,
      Instant endedAt,
      UUID correctedByAccountId,
      OffsetDateTime correctedAt,
      Long version,
      Long lastChangedPathVersion,
      Instant createdAt,
      Instant updatedAt,
      Integer startIndex,
      Integer endIndex,
      String startPointId,
      String endPointId) {
    this.id = id;
    this.searchPathId = searchPathId;
    this.movementType = movementType;
    this.movementTypeSource = movementTypeSource;
    this.geometry = geometry;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.correctedByAccountId = correctedByAccountId;
    this.correctedAt = correctedAt;
    this.version = version == null ? 1L : version;
    this.lastChangedPathVersion = lastChangedPathVersion;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.startIndex = startIndex == null ? -1 : startIndex;
    this.endIndex = endIndex == null ? -1 : endIndex;
    this.startPointId = startPointId;
    this.endPointId = endPointId;
  }

  public void assignPointRange(
      int startIndex, int endIndex, String startPointId, String endPointId) {
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.startPointId = startPointId;
    this.endPointId = endPointId;
  }

  public void correct(
      MovementType movementType, UUID correctedByAccountId, OffsetDateTime correctedAt) {
    this.movementType = movementType;
    this.movementTypeSource = MovementTypeSource.MANUAL;
    this.correctedByAccountId = correctedByAccountId;
    this.correctedAt = correctedAt;
    this.version += 1L;
  }

  public void recordPathChange(long pathVersion) {
    this.lastChangedPathVersion = pathVersion;
  }

  public boolean canExtendWith(SearchPathSegment next) {
    return movementTypeSource == MovementTypeSource.AUTO
        && movementType == next.movementType
        && endIndex >= 0
        && endIndex + 1 == next.startIndex
        && geometry != null
        && geometry.getNumPoints() >= pointCount()
        && pointCount() + next.pointCount() <= MAX_AUTO_POINT_COUNT;
  }

  public void extendWith(SearchPathSegment next, long pathVersion, Instant updatedAt) {
    Coordinate[] coordinates =
        Stream.concat(
                Arrays.stream(sourceCoordinates()), Arrays.stream(next.sourceCoordinates()))
            .toArray(Coordinate[]::new);
    LineString extended = geometry.getFactory().createLineString(coordinates);
    extended.setSRID(geometry.getSRID());
    this.geometry = extended;
    this.endIndex = next.endIndex;
    this.endPointId = next.endPointId;
    this.endedAt = next.endedAt;
    this.version += 1L;
    this.lastChangedPathVersion = pathVersion;
    this.updatedAt = updatedAt;
  }

  private int pointCount() {
    return endIndex - startIndex + 1;
  }

  // 좌표 하나짜리 구간의 도형에는 같은 좌표가 두 개 들어 있으므로, 이어 붙일 때는 순번 범위의 좌표 수만큼만 쓴다.
  private Coordinate[] sourceCoordinates() {
    return Arrays.copyOf(geometry.getCoordinates(), pointCount());
  }
}
