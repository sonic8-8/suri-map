package com.surimap.api.controller.path.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LineStringGeometryJson {

  private String type;
  private List<List<Double>> coordinates;

  @Builder
  private LineStringGeometryJson(String type, List<List<Double>> coordinates) {
    this.type = type;
    this.coordinates = coordinates;
  }

  public static LineStringGeometryJson from(List<List<Double>> coordinates) {
    return LineStringGeometryJson.builder()
        .type("LineString")
        .coordinates(coordinates == null ? List.of() : coordinates)
        .build();
  }
}
