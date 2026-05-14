package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.exception.TileUnavailableException;
import com.surimap.offlinepackage.service.TileserverGlTileService;
import com.surimap.offlinepackage.service.TileserverProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@DisplayName("TileserverGlTileService")
class TileserverGlTileServiceTest {

  private static final MediaType APPLICATION_X_PROTOBUF =
      MediaType.valueOf("application/x-protobuf");

  private final RestTemplate restTemplate = new RestTemplate();
  private final TileserverProperties properties = new TileserverProperties();
  private MockRestServiceServer server;
  private TileserverGlTileService tileService;

  @BeforeEach
  void setUp() {
    properties.setBaseUrl("http://tileserver-gl:8080");
    server = MockRestServiceServer.createServer(restTemplate);
    tileService = new TileserverGlTileService(restTemplate, properties);
  }

  @Test
  @DisplayName("style JSON은 tileserver-gl에서 가져오되 public /tiles URL만 허용한다")
  void getStyleFetchesTileserverGlStyleWithLocalTileUrls() {
    server
        .expect(requestTo("http://tileserver-gl:8080/styles/osm-local/style.json"))
        .andRespond(
            withSuccess(
                """
                {
                  "version": 8,
                  "sources": {
                    "osm-local": {
                      "type": "vector",
                      "tiles": ["/tiles/osm-local/{z}/{x}/{y}.pbf"],
                      "minzoom": 0,
                      "maxzoom": 16
                    }
                  },
                  "layers": [
                    {
                      "id": "road-line",
                      "type": "line",
                      "source": "osm-local",
                      "source-layer": "transportation"
                    }
                  ],
                  "metadata": {
                    "attribution": "OpenStreetMap contributors / OpenMapTiles"
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    TileStyleResponse style = tileService.getStyle("osm-local");

    assertThat(style.version()).isEqualTo(8);
    assertThat(style.sources()).containsKey("osm-local");
    assertThat(style.layers()).hasSize(1);
    assertThat(style.metadata())
        .containsEntry("attribution", "OpenStreetMap contributors / OpenMapTiles");
    server.verify();
  }

  @Test
  @DisplayName("외부 tile URL이 포함된 style은 거부한다")
  void getStyleRejectsExternalTileUrls() {
    server
        .expect(requestTo("http://tileserver-gl:8080/styles/osm-local/style.json"))
        .andRespond(
            withSuccess(
                """
                {
                  "version": 8,
                  "sources": {
                    "osm-local": {
                      "type": "vector",
                      "tiles": ["https://example.com/tiles/osm-local/{z}/{x}/{y}.pbf"]
                    }
                  },
                  "layers": [],
                  "metadata": {}
                }
                """,
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> tileService.getStyle("osm-local"))
        .isInstanceOf(TileUnavailableException.class);
    server.verify();
  }

  @Test
  @DisplayName("gzip pbf 응답은 content encoding을 보존한다")
  void getTilePreservesGzipContentEncoding() {
    byte[] compressedTileBytes = new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00};
    server
        .expect(requestTo("http://tileserver-gl:8080/data/osm-local/16/55877/25377.pbf"))
        .andRespond(
            withSuccess(compressedTileBytes, APPLICATION_X_PROTOBUF)
                .header(HttpHeaders.CONTENT_ENCODING, "gzip"));

    TileBlobResponse tile = tileService.getTile("osm-local", 16, 55877, 25377);

    assertThat(tile.contentType()).isEqualTo(APPLICATION_X_PROTOBUF);
    assertThat(tile.bytes()).isEqualTo(compressedTileBytes);
    assertThat(tile.contentEncoding()).isEqualTo("gzip");
    server.verify();
  }
}
