package com.surimap.domain.path;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상황판 페이지에서 경로 하나의 구간을 읽을 조건이다. 여러 경로를 한 SQL로 읽을 때 사용한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathBoardSegmentQuery {
  private UUID pathId;
  private Integer before;
  private Long lowerVersion;
  private Long upperVersion;
  private int limit;

  @Builder
  private SearchPathBoardSegmentQuery(
      UUID pathId, Integer before, Long lowerVersion, Long upperVersion, int limit) {
    this.pathId = pathId;
    this.before = before;
    this.lowerVersion = lowerVersion;
    this.upperVersion = upperVersion;
    this.limit = limit;
  }
}
