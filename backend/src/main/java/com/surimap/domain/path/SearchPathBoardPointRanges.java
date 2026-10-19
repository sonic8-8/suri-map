package com.surimap.domain.path;

import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상황판 좌표를 읽을 경로별 순번 범위다. 범위 수와 관계없이 배열 인자 3개로 넘겨 바인딩 인자 상한에 걸리지 않는다. 결과의 범위 순서는 목록 순서(1부터)다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathBoardPointRanges {
  private String[] pathIds;
  private Integer[] startOrders;
  private Integer[] endOrders;

  public static SearchPathBoardPointRanges from(List<SearchPathSegment> ranges) {
    SearchPathBoardPointRanges result = new SearchPathBoardPointRanges();
    result.pathIds =
        ranges.stream().map(range -> range.getSearchPathId().toString()).toArray(String[]::new);
    result.startOrders =
        ranges.stream().map(SearchPathSegment::getStartIndex).toArray(Integer[]::new);
    result.endOrders = ranges.stream().map(SearchPathSegment::getEndIndex).toArray(Integer[]::new);
    return result;
  }
}
