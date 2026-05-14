package com.surimap.offlinepackage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TileStyleResponse {

  private final int version;
  private final Map<String, Object> sources;
  private final List<Map<String, Object>> layers;
  private final Map<String, Object> metadata;
  private final String glyphs;

  public TileStyleResponse(
      int version,
      Map<String, ?> sources,
      List<? extends Map<String, ?>> layers,
      Map<String, ?> metadata) {
    this(version, sources, layers, metadata, null);
  }

  public TileStyleResponse(
      int version,
      Map<String, ?> sources,
      List<? extends Map<String, ?>> layers,
      Map<String, ?> metadata,
      String glyphs) {
    this.version = version;
    this.sources = copyMap(sources);
    this.layers = copyLayers(layers);
    this.metadata = copyMap(metadata);
    this.glyphs = glyphs;
  }

  public int getVersion() {
    return version;
  }

  public int version() {
    return version;
  }

  public Map<String, Object> getSources() {
    return sources;
  }

  public Map<String, Object> sources() {
    return sources;
  }

  public List<Map<String, Object>> getLayers() {
    return layers;
  }

  public List<Map<String, Object>> layers() {
    return layers;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }

  public String getGlyphs() {
    return glyphs;
  }

  public String glyphs() {
    return glyphs;
  }

  private static Map<String, Object> copyMap(Map<String, ?> values) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  private static List<Map<String, Object>> copyLayers(List<? extends Map<String, ?>> values) {
    List<Map<String, Object>> copied = new ArrayList<>();
    for (Map<String, ?> value : values) {
      copied.add(copyMap(value));
    }
    return Collections.unmodifiableList(copied);
  }
}
