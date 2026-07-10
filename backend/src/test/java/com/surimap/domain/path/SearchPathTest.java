package com.surimap.domain.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchPathTest {

  @Test
  void appendAcceptedPointsSetsStartTimeFromFirstPoint() {
    SearchPath path = SearchPath.builder().build();
    SearchPathPoint first = point("point-1", "2026-04-28T09:00:00+09:00");
    SearchPathPoint second = point("point-2", "2026-04-28T09:00:05+09:00");

    path.appendAcceptedPoints(List.of(first));
    path.appendAcceptedPoints(List.of(second));

    assertThat(path.getPoints()).containsExactly(first, second);
    assertThat(path.getStartedAt()).isEqualTo(first.getClientTs().toInstant());
  }

  @Test
  void correctSegmentAppliesManualCorrectionAndPreservesPathVersion() {
    UUID correctedByAccountId = UUID.fromString("63000000-0000-0000-0000-000000002621");
    OffsetDateTime correctedAt = OffsetDateTime.parse("2026-04-28T09:10:00+09:00");
    SearchPathSegment target =
        SearchPathSegment.builder()
            .id(segmentId("segment-1"))
            .version(3L)
            .movementType(MovementType.VEHICLE)
            .movementTypeSource(MovementTypeSource.AUTO)
            .startIndex(2)
            .endIndex(5)
            .startPointId("point-2")
            .endPointId("point-5")
            .build();
    SearchPathSegment untouched = segment("segment-2");
    SearchPath path = SearchPath.builder().version(7L).segments(List.of(target, untouched)).build();

    SearchPathSegment corrected =
        path.correctSegment(
            target.getId().toString(), MovementType.FOOT, correctedByAccountId, correctedAt);

    assertThat(corrected.getId()).isEqualTo(target.getId());
    assertThat(corrected.getVersion()).isEqualTo(4L);
    assertThat(corrected.getMovementType()).isEqualTo(MovementType.FOOT);
    assertThat(corrected.getMovementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);
    assertThat(corrected.getStartIndex()).isEqualTo(target.getStartIndex());
    assertThat(corrected.getEndIndex()).isEqualTo(target.getEndIndex());
    assertThat(corrected.getStartPointId()).isEqualTo(target.getStartPointId());
    assertThat(corrected.getEndPointId()).isEqualTo(target.getEndPointId());
    assertThat(corrected.getCorrectedByAccountId()).isEqualTo(correctedByAccountId);
    assertThat(corrected.getCorrectedAt()).isEqualTo(correctedAt);
    assertThat(path.getSegments()).containsExactly(corrected, untouched);
    assertThat(path.getVersion()).isEqualTo(7L);
  }

  @Test
  void correctSegmentRejectsUnknownSegment() {
    SearchPath path = SearchPath.builder().segments(List.of(segment("segment-1"))).build();

    assertThatThrownBy(
            () ->
                path.correctSegment(
                    "unknown-segment",
                    MovementType.FOOT,
                    UUID.fromString("63000000-0000-0000-0000-000000002621"),
                    OffsetDateTime.parse("2026-04-28T09:10:00+09:00")))
        .isInstanceOf(SearchPathApiException.class)
        .hasMessage("write_conflict");
  }

  private static SearchPathPoint point(String pointId, String clientTs) {
    return SearchPathPoint.builder()
        .pointId(pointId)
        .clientTs(OffsetDateTime.parse(clientTs))
        .lon(BigDecimal.ZERO)
        .lat(BigDecimal.ZERO)
        .speedMps(BigDecimal.ZERO)
        .horizontalAccuracyM(5)
        .build();
  }

  private static SearchPathSegment segment(String id) {
    return SearchPathSegment.builder()
        .id(segmentId(id))
        .movementType(MovementType.UNKNOWN)
        .movementTypeSource(MovementTypeSource.AUTO)
        .startIndex(0)
        .endIndex(1)
        .startPointId("point-1")
        .endPointId("point-2")
        .build();
  }

  private static UUID segmentId(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
  }
}
