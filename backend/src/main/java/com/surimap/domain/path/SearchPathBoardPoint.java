package com.surimap.domain.path;

import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상황판 구간 좌표를 여러 경로에서 한 번에 읽은 결과다. 요청한 순번 범위와 순번으로 구간에 다시 나눈다.
 *
 * <p>좌표와 시각은 DB에서 숫자형으로 받는다. 행마다 BigDecimal과 OffsetDateTime을 만들지 않으려는 것이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathBoardPoint {
  // 요청한 순번 범위의 위치(1부터)다. 경로 ID를 행마다 받지 않고 범위로 경로를 찾는다.
  private int rangeOrder;
  private int pointOrder;
  private double lon;
  private double lat;
  private long clientTsEpochMicros;
  // 294247-01-01T00:00:00Z 이상 시각과 무한 시각만 채운다. epoch 마이크로초가 294247-01-10 근처에서 넘쳐 그보다 이른 기준을 쓴다.
  // 무한 시각은 JDBC가 OffsetDateTime.MAX와 MIN으로 읽는다.
  private OffsetDateTime outOfRangeClientTs;

  public Instant getClientTs() {
    if (outOfRangeClientTs != null) {
      return outOfRangeClientTs.toInstant();
    }
    return Instant.ofEpochSecond(
        Math.floorDiv(clientTsEpochMicros, 1_000_000L),
        Math.floorMod(clientTsEpochMicros, 1_000_000L) * 1_000L);
  }
}
