package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.exception.TileUnavailableException;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.service.LocalTileService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

@DisplayName("LocalTileService")
class LocalTileServiceTest {

  private static final String STYLE_ID = OfflinePackageManifestFixtures.STYLE_ID;
  private static final String TILE_URL = "/tiles/osm-local/{z}/{x}/{y}.pbf";
  private static final MediaType APPLICATION_X_PROTOBUF =
      MediaType.valueOf("application/x-protobuf");
  private static final byte[] MINIMAL_VECTOR_TILE_PREFIX =
      new byte[] {
        0x1a,
        0x10,
        0x0a,
        0x09,
        'l',
        'a',
        'n',
        'd',
        'c',
        'o',
        'v',
        'e',
        'r',
        0x28,
        (byte) 0x80,
        0x20,
        0x78,
        0x02
      };
  private static final List<String> EXTERNAL_HOST_STRINGS =
      List.of("tile.openstreetmap.org", "mapbox.com", "googleapis.com");

  private final LocalTileService tileService = new LocalTileService();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("osm-local style uses local vector tiles and no external hosts")
  void getStyleReturnsLocalOnlyMapLibreStyle() throws Exception {
    TileStyleResponse style = tileService.getStyle(STYLE_ID);

    assertThat(style.version()).isEqualTo(8);

    assertThat(style.sources()).containsKey(STYLE_ID);
    assertThat(style.sources().get(STYLE_ID)).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> source = (Map<String, Object>) style.sources().get(STYLE_ID);
    assertThat(source)
        .containsEntry("type", "vector")
        .containsEntry("minzoom", OfflinePackageManifestFixtures.MIN_Z)
        .containsEntry("maxzoom", OfflinePackageManifestFixtures.MAX_Z)
        .containsEntry("attribution", "OpenStreetMap contributors / OpenMapTiles");
    assertThat(source.get("tiles")).isEqualTo(List.of(TILE_URL));

    assertThat(style.metadata().get("attribution").toString())
        .satisfiesAnyOf(
            attribution -> assertThat(attribution).contains("OpenStreetMap"),
            attribution -> assertThat(attribution).contains("OpenMapTiles"),
            attribution -> assertThat(attribution).contains("OSM"));

    String serializedStyle = objectMapper.writeValueAsString(style);
    String styleMapText =
        style.sources().toString() + style.layers().toString() + style.metadata().toString();
    assertThat(serializedStyle).doesNotContain(EXTERNAL_HOST_STRINGS);
    assertThat(styleMapText).doesNotContain(EXTERNAL_HOST_STRINGS);
  }

  @ParameterizedTest(name = "{0}/{1}/{2}")
  @MethodSource("localFixtureTiles")
  @DisplayName("fixture tiles return protobuf content type and deterministic vector tile blobs")
  void getTileReturnsDeterministicLocalFixtureBlob(
      int z, int x, int y, int expectedSize, String fixtureChecksumToken) {
    TileBlobResponse tile = tileService.getTile(STYLE_ID, z, x, y);
    TileBlobResponse repeatedTile = tileService.getTile(STYLE_ID, z, x, y);

    assertThat(tile.contentType()).isEqualTo(APPLICATION_X_PROTOBUF);
    assertThat(tile.bytes()).hasSize(expectedSize).startsWith(MINIMAL_VECTOR_TILE_PREFIX);
    assertThat(sha256(tile.bytes())).isEqualTo(fixtureChecksumToken);
    assertThat(repeatedTile.bytes()).isEqualTo(tile.bytes());
    assertUnknownLengthDelimitedPaddingConsumesRest(tile.bytes());
  }

  @Test
  @DisplayName("unsupported style throws tile unavailable")
  void unsupportedStyleThrowsTileUnavailable() {
    assertThatThrownBy(() -> tileService.getStyle("external-mapbox"))
        .isInstanceOf(TileUnavailableException.class);
  }

  @Test
  @DisplayName("unsupported tile throws tile unavailable")
  void unsupportedTileThrowsTileUnavailable() {
    assertThatThrownBy(
            () ->
                tileService.getTile(
                    STYLE_ID,
                    OfflinePackageManifestFixtures.MIN_Z - 1,
                    OfflinePackageManifestFixtures.MIN_X,
                    OfflinePackageManifestFixtures.MIN_Y))
        .isInstanceOf(TileUnavailableException.class);
  }

  private static Stream<Arguments> localFixtureTiles() {
    return OfflinePackageManifestFixtures.tileManifest().tiles().stream()
        .map(tile -> arguments(tile.z(), tile.x(), tile.y(), tile.bytes(), tile.checksum()));
  }

  private static String sha256(byte[] bytes) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private static void assertUnknownLengthDelimitedPaddingConsumesRest(byte[] bytes) {
    int offset = MINIMAL_VECTOR_TILE_PREFIX.length;
    assertThat(bytes[offset++]).isEqualTo((byte) 0x22);

    Varint paddingLength = readVarint(bytes, offset);
    int paddingEnd = paddingLength.nextOffset() + paddingLength.value();
    assertThat(paddingEnd).isEqualTo(bytes.length);
  }

  private static Varint readVarint(byte[] bytes, int offset) {
    int value = 0;
    int shift = 0;
    while (offset < bytes.length) {
      int current = bytes[offset++] & 0xff;
      value |= (current & 0x7f) << shift;
      if ((current & 0x80) == 0) {
        return new Varint(value, offset);
      }
      shift += 7;
    }
    throw new IllegalArgumentException("unterminated varint");
  }

  private record Varint(int value, int nextOffset) {}
}
