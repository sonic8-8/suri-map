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
import java.util.List;
import java.util.Map;
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
	                  "glyphs": "/tiles/fonts/{fontstack}/{range}.pbf",
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
    assertThat(style.glyphs()).isEqualTo("/tiles/fonts/{fontstack}/{range}.pbf");
    assertThat(style.metadata())
        .containsEntry("attribution", "OpenStreetMap contributors / OpenMapTiles");
    server.verify();
  }

  @Test
  @DisplayName("tileserver-gl native /data, /fonts 경로는 public /tiles 경로로 정규화한다")
  void getStyleNormalizesTileserverGlNativePaths() {
    server
        .expect(requestTo("http://tileserver-gl:8080/styles/osm-local/style.json"))
        .andRespond(
            withSuccess(
                """
                {
                  "version": 8,
                  "glyphs": "/fonts/{fontstack}/{range}.pbf",
                  "sources": {
                    "osm-local": {
                      "type": "vector",
                      "tiles": ["/data/osm-local/{z}/{x}/{y}.pbf"]
                    },
                    "gwangju-building-labels": {
                      "type": "vector",
                      "tiles": ["http://tileserver-gl:8080/data/gwangju-building-labels/{z}/{x}/{y}.pbf"]
                    }
                  },
                  "layers": [],
                  "metadata": {}
                }
                """,
                MediaType.APPLICATION_JSON));

    TileStyleResponse style = tileService.getStyle("osm-local");

    assertThat(tileUrls(style, "osm-local")).containsExactly("/tiles/osm-local/{z}/{x}/{y}.pbf");
    assertThat(tileUrls(style, "gwangju-building-labels"))
        .containsExactly("/tiles/gwangju-building-labels/{z}/{x}/{y}.pbf");
    assertThat(style.glyphs()).isEqualTo("/tiles/fonts/{fontstack}/{range}.pbf");
    server.verify();
  }

  @Test
  @DisplayName("tileserver-gl이 절대 /fonts glyph URL로 바꿔도 public /tiles/fonts 경로로 정규화한다")
  void getStyleNormalizesNativeTileserverGlyphUrl() {
    server
        .expect(requestTo("http://tileserver-gl:8080/styles/osm-local/style.json"))
        .andRespond(
            withSuccess(
                """
                {
                  "version": 8,
                  "glyphs": "http://tileserver-gl:8080/fonts/{fontstack}/{range}.pbf",
                  "sources": {
                    "osm-local": {
                      "type": "vector",
                      "tiles": ["/tiles/osm-local/{z}/{x}/{y}.pbf"]
                    }
                  },
                  "layers": [],
                  "metadata": {}
                }
                """,
                MediaType.APPLICATION_JSON));

    TileStyleResponse style = tileService.getStyle("osm-local");

    assertThat(style.glyphs()).isEqualTo("/tiles/fonts/{fontstack}/{range}.pbf");
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
  @DisplayName("외부 glyph URL이 포함된 style은 거부한다")
  void getStyleRejectsExternalGlyphUrl() {
    server
        .expect(requestTo("http://tileserver-gl:8080/styles/osm-local/style.json"))
        .andRespond(
            withSuccess(
                """
	                {
	                  "version": 8,
	                  "glyphs": "https://example.com/fonts/{fontstack}/{range}.pbf",
	                  "sources": {
	                    "osm-local": {
	                      "type": "vector",
		                      "tiles": ["/tiles/osm-local/{z}/{x}/{y}.pbf"]
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

  @Test
  @DisplayName("glyph pbf 응답은 tileserver-gl fonts 경로에서 가져온다")
  void getGlyphFetchesTileserverGlFontRange() {
    byte[] glyphBytes = new byte[] {0x1a, 0x02, 0x08, 0x01};
    server
        .expect(
            requestTo("http://tileserver-gl:8080/fonts/Noto%20Sans%20Regular/0-255.pbf"))
        .andRespond(withSuccess(glyphBytes, APPLICATION_X_PROTOBUF));

    TileBlobResponse glyph = tileService.getGlyph("Noto Sans Regular", "0-255");

    assertThat(glyph.contentType()).isEqualTo(APPLICATION_X_PROTOBUF);
    assertThat(glyph.bytes()).isEqualTo(glyphBytes);
    server.verify();
  }

  @SuppressWarnings("unchecked")
  private static List<String> tileUrls(TileStyleResponse style, String sourceId) {
    Map<String, Object> source = (Map<String, Object>) style.sources().get(sourceId);
    return (List<String>) source.get("tiles");
  }
}
