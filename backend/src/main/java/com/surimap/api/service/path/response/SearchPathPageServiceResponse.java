package com.surimap.api.service.path.response;

import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.SearchPathStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchPathPageServiceResponse {
  private List<Path> paths;
  private UUID nextSearchPathId;
  private boolean hasMore;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Path {
    private UUID id;
    private UUID accountId;
    private UUID opId;
    private SearchPathStatus status;
    private long version;
    private Long baselineVersion;
    private Long appliedVersion;
    private Long targetVersion;
    private Integer beforeStartPointOrder;
    private Boolean completed;
    private List<Segment> segments;
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Segment {
    private UUID id;
    private long version;
    private int startPointOrder;
    private int endPointOrder;
    private MovementType movementType;
    private List<List<Double>> coordinates;
    private Instant startedAt;
    private Instant endedAt;
  }
}
