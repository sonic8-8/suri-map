package com.surimap.offlinepackage.service;

import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.exception.TileUnavailableException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@ConditionalOnProperty(name = "tileserver.mode", havingValue = "tileserver-gl")
public class TileserverGlTileService implements TileService {

  private static final MediaType APPLICATION_X_PROTOBUF =
      MediaType.valueOf("application/x-protobuf");
  private static final String PUBLIC_GLYPH_PREFIX = "/tiles/fonts/";
  private static final String NATIVE_GLYPH_PREFIX = "/fonts/";

  private final RestTemplate restTemplate;
  private final TileserverProperties properties;

  public TileserverGlTileService(
      @Qualifier("tileserverRestTemplate") RestTemplate restTemplate,
      TileserverProperties properties) {
    this.restTemplate = restTemplate;
    this.properties = properties;
  }

  @Override
  public TileStyleResponse getStyle(String styleId) {
    try {
      ResponseEntity<Map<String, Object>> response =
          restTemplate.exchange(
              properties.styleUri(styleId),
              HttpMethod.GET,
              null,
              new ParameterizedTypeReference<>() {});
      Map<String, Object> body = response.getBody();
      if (!response.getStatusCode().is2xxSuccessful() || body == null) {
        throw new TileUnavailableException();
      }
      return styleResponse(body);
    } catch (RestClientException | ClassCastException | IllegalArgumentException exception) {
      throw new TileUnavailableException();
    }
  }

  @Override
  public TileBlobResponse getTile(String style, int z, int x, int y) {
    try {
      ResponseEntity<byte[]> response =
          restTemplate.exchange(
              properties.tileUri(style, z, x, y), HttpMethod.GET, null, byte[].class);
      if (response.getStatusCode().value() == HttpStatus.NO_CONTENT.value()) {
        // 데이터가 없는 구역은 오류 대신 레이어가 없는 빈 MVT로 전달한다.
        return new TileBlobResponse(APPLICATION_X_PROTOBUF, new byte[0]);
      }
      byte[] body = response.getBody();
      if (!response.getStatusCode().is2xxSuccessful() || body == null) {
        throw new TileUnavailableException();
      }
      MediaType contentType = response.getHeaders().getContentType();
      String contentEncoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
      return new TileBlobResponse(
          contentType == null ? APPLICATION_X_PROTOBUF : contentType, body, contentEncoding);
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new TileUnavailableException();
    }
  }

  @Override
  public TileBlobResponse getGlyph(String fontStack, String range) {
    try {
      ResponseEntity<byte[]> response =
          restTemplate.exchange(
              properties.glyphUri(fontStack, range), HttpMethod.GET, null, byte[].class);
      byte[] body = response.getBody();
      if (!response.getStatusCode().is2xxSuccessful() || body == null) {
        throw new TileUnavailableException();
      }
      MediaType contentType = response.getHeaders().getContentType();
      String contentEncoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
      return new TileBlobResponse(
          contentType == null ? APPLICATION_X_PROTOBUF : contentType, body, contentEncoding);
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new TileUnavailableException();
    }
  }

  @SuppressWarnings("unchecked")
  private TileStyleResponse styleResponse(Map<String, Object> body) {
    Object versionValue = body.get("version");
    if (!(versionValue instanceof Number version)) {
      throw new TileUnavailableException();
    }
    Map<String, ?> sources = (Map<String, ?>) body.get("sources");
    List<? extends Map<String, ?>> layers = (List<? extends Map<String, ?>>) body.get("layers");
    Map<String, ?> metadata =
        body.get("metadata") instanceof Map<?, ?> metadataMap
            ? (Map<String, ?>) metadataMap
            : Collections.emptyMap();
    if (sources == null || layers == null) {
      throw new TileUnavailableException();
    }
    String glyphs = normalizeGlyphUrl(body.get("glyphs"));
    return new TileStyleResponse(
        version.intValue(), normalizeTileSources(sources), layers, metadata, glyphs);
  }

  private Map<String, Object> normalizeTileSources(Map<String, ?> sources) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, ?> entry : sources.entrySet()) {
      Object sourceValue = entry.getValue();
      if (sourceValue instanceof Map<?, ?> source) {
        normalized.put(entry.getKey(), normalizeTileSource(source));
      } else {
        normalized.put(entry.getKey(), sourceValue);
      }
    }
    return normalized;
  }

  private Map<String, Object> normalizeTileSource(Map<?, ?> source) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        continue;
      }
      Object value = entry.getValue();
      normalized.put(
          key,
          "tiles".equals(key) && value instanceof List<?> tiles ? normalizeTileUrls(tiles) : value);
    }
    return normalized;
  }

  private List<String> normalizeTileUrls(List<?> tiles) {
    List<String> normalized = new ArrayList<>();
    for (Object tileUrl : tiles) {
      if (!(tileUrl instanceof String url)) {
        throw new TileUnavailableException();
      }
      normalized.add(normalizeTileUrl(url));
    }
    return normalized;
  }

  private String normalizeTileUrl(String url) {
    String value = stripTileserverBaseUrl(url);
    if (value.startsWith("/tiles/")) {
      return value;
    }
    if (value.startsWith("/data/")) {
      return "/tiles/" + value.substring("/data/".length());
    }
    throw new TileUnavailableException();
  }

  private String normalizeGlyphUrl(Object glyphs) {
    if (glyphs == null) {
      return null;
    }
    if (!(glyphs instanceof String glyphUrl)) {
      throw new TileUnavailableException();
    }
    String value = stripTileserverBaseUrl(glyphUrl);
    if (value.startsWith(PUBLIC_GLYPH_PREFIX)) {
      return value;
    }
    if (value.startsWith(NATIVE_GLYPH_PREFIX)) {
      return "/tiles" + value;
    }
    throw new TileUnavailableException();
  }

  private String stripTileserverBaseUrl(String url) {
    String baseUrl = normalizedBaseUrl();
    if (!baseUrl.isBlank() && url.startsWith(baseUrl + "/")) {
      return url.substring(baseUrl.length());
    }
    return url;
  }

  private String normalizedBaseUrl() {
    String baseUrl = properties.getBaseUrl();
    return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
  }
}
