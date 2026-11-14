package com.surimap.domain.path;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상황판 좌표를 요청한 순번 범위마다 한 행으로 읽은 결과다. 좌표는 범위의 시작 순번부터 순서대로 배열에 담긴다.
 *
 * <p>좌표마다 행과 객체를 만들지 않으려고 경도와 위도를 배열로 받는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathBoardPointRun {
  // 요청한 순번 범위의 위치(1부터)다.
  private int rangeOrder;
  private int startPointOrder;
  private int pointCount;
  private Double[] lons;
  private Double[] lats;

  // 순번은 경로마다 하나뿐이고(기본 키) 범위 안 좌표만 세므로, 개수가 범위 길이와 같으면 빠짐없이 있다.
  public boolean hasAllPointsThrough(int endPointOrder) {
    return pointCount == (long) endPointOrder - startPointOrder + 1;
  }

  public boolean contains(int startOrder, int endOrder) {
    return startOrder >= startPointOrder && (long) endOrder < (long) startPointOrder + pointCount;
  }
}
