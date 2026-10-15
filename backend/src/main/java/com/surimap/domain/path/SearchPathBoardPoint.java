package com.surimap.domain.path;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상황판 구간 좌표를 여러 경로에서 한 번에 읽은 결과다. 경로와 순번으로 구간에 다시 나눈다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathBoardPoint {
  private UUID searchPathId;
  private int pointOrder;
  private BigDecimal lon;
  private BigDecimal lat;
  private OffsetDateTime clientTs;
}
