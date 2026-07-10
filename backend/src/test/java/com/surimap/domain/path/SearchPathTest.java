package com.surimap.domain.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
    assertThat(path.getStartedAt()).isEqualTo(first.clientTs().toInstant());
  }

  @Test
  void correctSegmentAppliesManualCorrectionAndPreservesPathVersion() {
    UUID correctedByAccountId = UUID.fromString("63000000-0000-0000-0000-000000002621");
    OffsetDateTime correctedAt = OffsetDateTime.parse("2026-04-28T09:10:00+09:00");
    SearchPathSegment target =
        new SearchPathSegment(
            "segment-1",
            3L,
            MovementType.VEHICLE,
            MovementTypeSource.AUTO,
            2,
            5,
            "point-2",
            "point-5",
            null,
            null);
    SearchPathSegment untouched = segment("segment-2");
    SearchPath path = SearchPath.builder().version(7L).segments(List.of(target, untouched)).build();

    SearchPathSegment corrected =
        path.correctSegment(target.id(), MovementType.FOOT, correctedByAccountId, correctedAt);

    assertThat(corrected.id()).isEqualTo(target.id());
    assertThat(corrected.version()).isEqualTo(4L);
    assertThat(corrected.movementType()).isEqualTo(MovementType.FOOT);
    assertThat(corrected.movementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);
    assertThat(corrected.startIndex()).isEqualTo(target.startIndex());
    assertThat(corrected.endIndex()).isEqualTo(target.endIndex());
    assertThat(corrected.startPointId()).isEqualTo(target.startPointId());
    assertThat(corrected.endPointId()).isEqualTo(target.endPointId());
    assertThat(corrected.correctedByAccountId()).isEqualTo(correctedByAccountId);
    assertThat(corrected.correctedAt()).isEqualTo(correctedAt);
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
    return new SearchPathPoint(
        pointId,
        OffsetDateTime.parse(clientTs),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        5);
  }

  private static SearchPathSegment segment(String id) {
    return new SearchPathSegment(
        id,
        1L,
        MovementType.UNKNOWN,
        MovementTypeSource.AUTO,
        0,
        1,
        "point-1",
        "point-2",
        null,
        null);
  }
}
