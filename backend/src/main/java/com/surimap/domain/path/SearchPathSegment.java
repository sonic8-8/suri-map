package com.surimap.domain.path;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Geometry;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathSegment {

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
}
