package com.surimap.domain.path;

import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상황판 좌표를 요청한 순번 범위마다 한 행으로 읽은 결과다. 좌표는 범위의 시작 순번부터 순서대로 배열에 담긴다.
 *
 * <p>좌표마다 행과 객체를 만들지 않으려고 경도, 위도, 측정 시각을 배열로 받는다.
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
  private Long[] clientTsEpochMicros;
  // 294247-01-01T00:00:00Z 이상 시각이나 무한 시각이 있으면 참이다. 이 범위의 시각 배열은 쓰지 않고 좌표 행으로 다시 읽는다.
  private boolean hasOutOfRangeClientTs;
  // 좌표 행으로 다시 읽은 범위만 채운다.
  private Instant[] clientTimes;

  public static SearchPathBoardPointRun fromPoints(
      int rangeOrder, int startPointOrder, List<SearchPathBoardPoint> points) {
    SearchPathBoardPointRun result = new SearchPathBoardPointRun();
    result.rangeOrder = rangeOrder;
    result.startPointOrder = startPointOrder;
    result.pointCount = points.size();
    result.lons = points.stream().map(SearchPathBoardPoint::getLon).toArray(Double[]::new);
    result.lats = points.stream().map(SearchPathBoardPoint::getLat).toArray(Double[]::new);
    result.clientTimes =
        points.stream().map(SearchPathBoardPoint::getClientTs).toArray(Instant[]::new);
    return result;
  }

  // 순번은 경로마다 하나뿐이고(기본 키) 범위 안 좌표만 세므로, 개수가 범위 길이와 같으면 빠짐없이 있다.
  public boolean hasAllPointsThrough(int endPointOrder) {
    return pointCount == (long) endPointOrder - startPointOrder + 1;
  }

  public boolean contains(int startOrder, int endOrder) {
    return startOrder >= startPointOrder && (long) endOrder < (long) startPointOrder + pointCount;
  }

  public Instant getClientTs(int index) {
    if (clientTimes != null) {
      return clientTimes[index];
    }
    long micros = clientTsEpochMicros[index];
    return Instant.ofEpochSecond(
        Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
  }
}
