package com.surimap.offlinepackage.service;

import java.net.URI;
import org.springframework.web.util.UriComponentsBuilder;

public class TileserverProperties {

  private String mode = "fixture";
  private String baseUrl = "http://tileserver-gl:8080";

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  URI styleUri(String styleId) {
    return UriComponentsBuilder.fromUriString(normalizedBaseUrl())
        .pathSegment("styles", styleId, "style.json")
        .build()
        .toUri();
  }

  URI tileUri(String style, int z, int x, int y) {
    return UriComponentsBuilder.fromUriString(normalizedBaseUrl())
        .pathSegment("data", style, Integer.toString(z), Integer.toString(x), y + ".pbf")
        .build()
        .toUri();
  }

  URI glyphUri(String fontStack, String range) {
    return UriComponentsBuilder.fromUriString(normalizedBaseUrl())
        .pathSegment("fonts", fontStack, range + ".pbf")
        .build()
        .toUri();
  }

  private String normalizedBaseUrl() {
    return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
  }
}
