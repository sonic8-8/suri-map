package com.surimap.offlinepackage.controller;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.exception.TileChannelNotAllowedException;
import com.surimap.offlinepackage.service.TileService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.ForwardedHeaderUtils;

@RestController
@RequestMapping("/tiles")
public class TileController {

  private final TileService tileService;

  public TileController(TileService tileService) {
    this.tileService = tileService;
  }

  @GetMapping("/styles/{styleId}.json")
  public ResponseEntity<TileStyleResponse> style(
      @PathVariable String styleId, Authentication authentication, HttpServletRequest request) {
    requirePublicSession(authentication);
    return ResponseEntity.ok(withAbsoluteTileUrls(tileService.getStyle(styleId), request));
  }

  @GetMapping("/{style}/{z}/{x}/{y}.pbf")
  public ResponseEntity<byte[]> tile(
      @PathVariable String style,
      @PathVariable int z,
      @PathVariable int x,
      @PathVariable int y,
      Authentication authentication) {
    requirePublicSession(authentication);
    TileBlobResponse response = tileService.getTile(style, z, x, y);
    ResponseEntity.BodyBuilder responseBuilder =
        ResponseEntity.ok().contentType(response.getContentType());
    if (response.getContentEncoding() != null && !response.getContentEncoding().isBlank()) {
      responseBuilder.header(HttpHeaders.CONTENT_ENCODING, response.getContentEncoding());
    }
    return responseBuilder.body(response.getBytes());
  }

  @GetMapping("/fonts/{fontStack}/{range}.pbf")
  public ResponseEntity<byte[]> glyph(
      @PathVariable String fontStack,
      @PathVariable String range,
      Authentication authentication) {
    requirePublicSession(authentication);
    TileBlobResponse response = tileService.getGlyph(fontStack, range);
    ResponseEntity.BodyBuilder responseBuilder =
        ResponseEntity.ok().contentType(response.getContentType());
    if (response.getContentEncoding() != null && !response.getContentEncoding().isBlank()) {
      responseBuilder.header(HttpHeaders.CONTENT_ENCODING, response.getContentEncoding());
    }
    return responseBuilder.body(response.getBytes());
  }

  private static void requirePublicSession(Authentication authentication) {
    if (!(authentication instanceof SuriMapAuthentication suriMapAuthentication)
        || !suriMapAuthentication.isAuthenticated()
        || !isPublicChannel(suriMapAuthentication.getChannel())) {
      throw new TileChannelNotAllowedException();
    }
  }

  private static boolean isPublicChannel(Channel channel) {
    return channel == Channel.APP || channel == Channel.WEB;
  }

  private static TileStyleResponse withAbsoluteTileUrls(
      TileStyleResponse style, HttpServletRequest request) {
    String origin = requestOrigin(request);
    Map<String, Object> sources = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : style.sources().entrySet()) {
      Object sourceValue = entry.getValue();
      if (sourceValue instanceof Map<?, ?> source) {
        sources.put(entry.getKey(), sourceWithAbsoluteTileUrls(source, origin));
      } else {
        sources.put(entry.getKey(), sourceValue);
      }
    }
    return new TileStyleResponse(
        style.version(),
        sources,
        style.layers(),
        style.metadata(),
        absoluteGlyphUrl(style.glyphs(), origin));
  }

  private static String requestOrigin(HttpServletRequest request) {
    ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(request);
    URI requestUri = serverRequest.getURI();
    return ForwardedHeaderUtils.adaptFromForwardedHeaders(requestUri, serverRequest.getHeaders())
        .replacePath(null)
        .replaceQuery(null)
        .build()
        .toUriString();
  }

  private static Map<String, Object> sourceWithAbsoluteTileUrls(Map<?, ?> source, String origin) {
    Map<String, Object> copied = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String keyText)) {
        continue;
      }
      copied.put(
          keyText,
          "tiles".equals(keyText) && entry.getValue() instanceof List<?> tiles
              ? absoluteTileUrls(tiles, origin)
              : entry.getValue());
    }
    return copied;
  }

  private static List<String> absoluteTileUrls(List<?> tiles, String origin) {
    List<String> urls = new ArrayList<>();
    for (Object tile : tiles) {
      if (tile instanceof String url) {
        urls.add(url.startsWith("/tiles/") ? origin + url : url);
      }
    }
    return urls;
  }

  private static String absoluteGlyphUrl(String glyphs, String origin) {
    if (glyphs == null) {
      return null;
    }
    return glyphs.startsWith("/tiles/") ? origin + glyphs : glyphs;
  }
}
