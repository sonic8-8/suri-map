package com.surimap.offlinepackage.service;

import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.exception.TileUnavailableException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
    String glyphs = glyphUrl(body.get("glyphs"));
    validateLocalTileSources(sources);
    return new TileStyleResponse(version.intValue(), sources, layers, metadata, glyphs);
  }

  private static void validateLocalTileSources(Map<String, ?> sources) {
    for (Object sourceValue : sources.values()) {
      if (!(sourceValue instanceof Map<?, ?> source)) {
        continue;
      }
      Object tiles = source.get("tiles");
      if (!(tiles instanceof List<?> tileUrls)) {
        continue;
      }
      for (Object tileUrl : tileUrls) {
        if (!(tileUrl instanceof String url) || !url.startsWith("/tiles/")) {
          throw new TileUnavailableException();
        }
      }
    }
  }

  private String glyphUrl(Object glyphs) {
    if (glyphs == null) {
      return null;
    }
    if (!(glyphs instanceof String glyphUrl)) {
      throw new TileUnavailableException();
    }
    String path = glyphPath(glyphUrl);
    if (path.startsWith(PUBLIC_GLYPH_PREFIX)) {
      return path;
    }
    if (path.startsWith(NATIVE_GLYPH_PREFIX)) {
      return "/tiles" + path;
    }
    throw new TileUnavailableException();
  }

  private String glyphPath(String glyphUrl) {
    if (glyphUrl.startsWith("http://") || glyphUrl.startsWith("https://")) {
      String baseUrl = normalizedBaseUrl();
      if (!glyphUrl.startsWith(baseUrl + "/")) {
        throw new TileUnavailableException();
      }
      int schemeEnd = glyphUrl.indexOf("://") + 3;
      int pathStart = glyphUrl.indexOf('/', schemeEnd);
      if (pathStart < 0) {
        throw new TileUnavailableException();
      }
      return glyphUrl.substring(pathStart);
    }
    return glyphUrl;
  }

  private String normalizedBaseUrl() {
    String baseUrl = properties.getBaseUrl();
    return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
  }
}
