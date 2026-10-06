package com.surimap.api.controller.path.response;

import com.surimap.api.service.path.response.SearchPathPageServiceResponse.Segment;
import com.surimap.domain.path.MovementType;
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
public class SearchPathPageSegmentResponse {
  private UUID id;
  private String version;
  private int startPointOrder;
  private int endPointOrder;
  private MovementType movementType;
  private LineStringGeometryJson geometry;
  private Instant startedAt;
  private Instant endedAt;

  public static SearchPathPageSegmentResponse from(Segment segment) {
    List<List<Double>> coordinates = segment.getCoordinates();
    // 원본 GPS와 저장 순번은 유지하고, 표시용 LineString만 두 점으로 표현한다.
    if (coordinates.size() == 1) {
      coordinates = List.of(coordinates.get(0), coordinates.get(0));
    }
    return SearchPathPageSegmentResponse.builder()
        .id(segment.getId())
        .version(Long.toString(segment.getVersion()))
        .startPointOrder(segment.getStartPointOrder())
        .endPointOrder(segment.getEndPointOrder())
        .movementType(segment.getMovementType())
        .geometry(LineStringGeometryJson.from(coordinates))
        .startedAt(segment.getStartedAt())
        .endedAt(segment.getEndedAt())
        .build();
  }
}
