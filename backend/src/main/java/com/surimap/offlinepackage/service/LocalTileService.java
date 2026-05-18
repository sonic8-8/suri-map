package com.surimap.offlinepackage.service;

import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.exception.TileUnavailableException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "tileserver.mode", havingValue = "fixture", matchIfMissing = true)
public class LocalTileService implements TileService {

  private static final String STYLE_ID = "osm-local";
  private static final String TILE_URL = "/tiles/osm-local/{z}/{x}/{y}.pbf";
  private static final int MIN_Z = 15;
  private static final int MAX_Z = 16;
  private static final int MIN_X = 27935;
  private static final int MAX_X = 55873;
  private static final int MIN_Y = 12960;
  private static final int MAX_Y = 25923;
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
  private static final TileKey TILE_15_27935_12960 = new TileKey(15, 27935, 12960);
  private static final TileKey TILE_15_27936_12960 = new TileKey(15, 27936, 12960);
  private static final TileKey TILE_16_55870_25920 = new TileKey(16, 55870, 25920);
  private static final Map<TileKey, byte[]> TILE_BYTES =
      Map.of(
          TILE_15_27935_12960, vectorTileFixtureBytes(TILE_15_27935_12960, 18_432),
          TILE_15_27936_12960, vectorTileFixtureBytes(TILE_15_27936_12960, 20_480),
          TILE_16_55870_25920, vectorTileFixtureBytes(TILE_16_55870_25920, 24_576));
  private static final TileStyleResponse STYLE_RESPONSE =
      new TileStyleResponse(
          8,
          Map.of(
              STYLE_ID,
              Map.of(
                  "type",
                  "vector",
                  "tiles",
                  List.of(TILE_URL),
                  "minzoom",
                  MIN_Z,
                  "maxzoom",
                  MAX_Z,
                  "attribution",
                  "OpenStreetMap contributors / OpenMapTiles")),
          List.of(
              Map.of(
                  "id",
                  "local-fixture-background",
                  "type",
                  "background",
                  "paint",
                  Map.of("background-color", "#edf2e8")),
              Map.of(
                  "id",
                  "landcover-fill",
                  "type",
                  "fill",
                  "source",
                  STYLE_ID,
                  "source-layer",
                  "landcover",
                  "paint",
                  Map.of("fill-color", "#dfe8d7", "fill-opacity", 0.72)),
              Map.of(
                  "id",
                  "water-fill",
                  "type",
                  "fill",
                  "source",
                  STYLE_ID,
                  "source-layer",
                  "water",
                  "paint",
                  Map.of("fill-color", "#9bc7d9", "fill-opacity", 0.82)),
              Map.of(
                  "id",
                  "road-line",
                  "type",
                  "line",
                  "source",
                  STYLE_ID,
                  "source-layer",
                  "transportation",
                  "paint",
                  Map.of("line-color", "#ffffff", "line-width", 1.2))),
          Map.of("attribution", "OpenStreetMap contributors / OpenMapTiles"));

  @Override
  public TileStyleResponse getStyle(String styleId) {
    if (!STYLE_ID.equals(styleId)) {
      throw new TileUnavailableException();
    }
    return STYLE_RESPONSE;
  }

  @Override
  public TileBlobResponse getTile(String style, int z, int x, int y) {
    if (!STYLE_ID.equals(style) || !isInFixtureRange(z, x, y)) {
      throw new TileUnavailableException();
    }

    byte[] bytes = TILE_BYTES.get(new TileKey(z, x, y));
    if (bytes == null) {
      throw new TileUnavailableException();
    }
    return new TileBlobResponse(APPLICATION_X_PROTOBUF, bytes);
  }

  @Override
  public TileBlobResponse getGlyph(String fontStack, String range) {
    throw new TileUnavailableException();
  }

  public static List<LocalTileMetadata> manifestTiles() {
    return List.of(
        localTileMetadata(TILE_15_27935_12960),
        localTileMetadata(TILE_15_27936_12960),
        localTileMetadata(TILE_16_55870_25920));
  }

  private static boolean isInFixtureRange(int z, int x, int y) {
    return z >= MIN_Z && z <= MAX_Z && x >= MIN_X && x <= MAX_X && y >= MIN_Y && y <= MAX_Y;
  }

  private static LocalTileMetadata localTileMetadata(TileKey tileKey) {
    byte[] bytes = TILE_BYTES.get(tileKey);
    return new LocalTileMetadata(
        STYLE_ID, tileKey.z(), tileKey.x(), tileKey.y(), prefixedSha256(bytes), bytes.length);
  }

  private static byte[] vectorTileFixtureBytes(TileKey tileKey, int size) {
    int unknownFieldTagSize = 1;
    int unknownPayloadSize =
        unknownLengthDelimitedPayloadSize(
            size - MINIMAL_VECTOR_TILE_PREFIX.length - unknownFieldTagSize);

    byte[] bytes = new byte[size];
    System.arraycopy(MINIMAL_VECTOR_TILE_PREFIX, 0, bytes, 0, MINIMAL_VECTOR_TILE_PREFIX.length);

    int offset = MINIMAL_VECTOR_TILE_PREFIX.length;
    bytes[offset++] = 0x22;
    offset = writeVarint(bytes, offset, unknownPayloadSize);
    for (int index = 0; index < unknownPayloadSize; index++) {
      bytes[offset + index] = fixturePayloadByte(tileKey, index);
    }
    return bytes;
  }

  private static int unknownLengthDelimitedPayloadSize(int fieldBodySize) {
    for (int lengthBytes = 1; lengthBytes <= 5; lengthBytes++) {
      int payloadSize = fieldBodySize - lengthBytes;
      if (payloadSize >= 0 && varintSize(payloadSize) == lengthBytes) {
        return payloadSize;
      }
    }
    throw new IllegalArgumentException("fixture size cannot contain a protobuf padding field");
  }

  private static int writeVarint(byte[] bytes, int offset, int value) {
    while ((value & ~0x7f) != 0) {
      bytes[offset++] = (byte) ((value & 0x7f) | 0x80);
      value >>>= 7;
    }
    bytes[offset++] = (byte) value;
    return offset;
  }

  private static int varintSize(int value) {
    int size = 1;
    while ((value & ~0x7f) != 0) {
      size++;
      value >>>= 7;
    }
    return size;
  }

  private static byte fixturePayloadByte(TileKey tileKey, int index) {
    int value = tileKey.z() * 31 + tileKey.x() * 17 + tileKey.y() * 13 + index * 7;
    return (byte) value;
  }

  private static String prefixedSha256(byte[] bytes) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  public record LocalTileMetadata(
      String styleId, int z, int x, int y, String checksum, int bytes) {}

  private record TileKey(int z, int x, int y) {}
}
