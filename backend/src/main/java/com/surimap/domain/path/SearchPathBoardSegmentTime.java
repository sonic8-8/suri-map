package com.surimap.domain.path;

import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상황판 경로 구간의 시작 순번과 끝 순번에 있는 GPS 좌표의 측정 시각이다. 경계 좌표가 없으면 그 시각은 null이다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathBoardSegmentTime {
  private OffsetDateTime startedAt;
  private OffsetDateTime endedAt;

  public boolean hasBothTimes() {
    return startedAt != null && endedAt != null;
  }
}
