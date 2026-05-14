package com.surimap.path;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LineStringCoordinatesDeserializer extends JsonDeserializer<List<List<Double>>> {
  @Override
  public List<List<Double>> deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    JsonNode node = parser.getCodec().readTree(parser);
    JsonNode coordinatesNode = node.isArray() ? node : node.get("coordinates");
    if (coordinatesNode == null || !coordinatesNode.isArray()) {
      return List.of();
    }
    List<List<Double>> coordinates = new ArrayList<>();
    for (JsonNode coordinateNode : coordinatesNode) {
      if (coordinateNode.isArray() && coordinateNode.size() >= 2) {
        coordinates.add(List.of(coordinateNode.get(0).asDouble(), coordinateNode.get(1).asDouble()));
      }
    }
    return coordinates;
  }
}
