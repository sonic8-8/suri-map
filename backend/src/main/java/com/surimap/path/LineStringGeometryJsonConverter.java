package com.surimap.path;

import com.fasterxml.jackson.databind.util.StdConverter;
import java.util.List;

public class LineStringGeometryJsonConverter
    extends StdConverter<List<List<Double>>, LineStringGeometryJson> {
  @Override
  public LineStringGeometryJson convert(List<List<Double>> value) {
    return LineStringGeometryJson.from(value);
  }
}
