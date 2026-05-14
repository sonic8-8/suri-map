package com.surimap.path;

import java.util.List;

public record LineStringGeometryJson(String type, List<List<Double>> coordinates) {
  public static LineStringGeometryJson from(List<List<Double>> coordinates) {
    return new LineStringGeometryJson("LineString", coordinates == null ? List.of() : coordinates);
  }
}
