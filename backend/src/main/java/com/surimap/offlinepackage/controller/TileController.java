package com.surimap.offlinepackage.controller;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.exception.TileChannelNotAllowedException;
import com.surimap.offlinepackage.service.TileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tiles")
public class TileController {

  private final TileService tileService;

  public TileController(TileService tileService) {
    this.tileService = tileService;
  }

  @GetMapping("/styles/{styleId}.json")
  public ResponseEntity<TileStyleResponse> style(
      @PathVariable String styleId, Authentication authentication) {
    requirePublicSession(authentication);
    return ResponseEntity.ok(tileService.getStyle(styleId));
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
}
