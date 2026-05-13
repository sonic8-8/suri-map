package com.surimap.offlinepackage.service;

import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.exception.TileUnavailableException;
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
  private static final int MIN_X = 27925;
  private static final int MAX_X = 27960;
  private static final int MIN_Y = 12680;
  private static final int MAX_Y = 12720;
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
  private static final TileKey TILE_15_27925_12680 = new TileKey(15, 27925, 12680);
  private static final TileKey TILE_15_27926_12680 = new TileKey(15, 27926, 12680);
  private static final TileKey TILE_16_27925_12681 = new TileKey(16, 27925, 12681);
  private static final Map<TileKey, byte[]> TILE_BYTES =
      Map.of(
          TILE_15_27925_12680, vectorTileFixtureBytes(TILE_15_27925_12680, 18_432),
          TILE_15_27926_12680, vectorTileFixtureBytes(TILE_15_27926_12680, 20_480),
          TILE_16_27925_12681, vectorTileFixtureBytes(TILE_16_27925_12681, 24_576));
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
                  Map.of("line-color", "#ffffff", "line-width", 1.2)),
              Map.of(
                  "id",
                  "place-label",
                  "type",
                  "symbol",
                  "source",
                  STYLE_ID,
                  "source-layer",
                  "place",
                  "layout",
                  Map.of("text-field", List.of("get", "name"), "text-size", 12),
                  "paint",
                  Map.of("text-color", "#2b3740"))),
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

  private static boolean isInFixtureRange(int z, int x, int y) {
    return z >= MIN_Z && z <= MAX_Z && x >= MIN_X && x <= MAX_X && y >= MIN_Y && y <= MAX_Y;
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

  private record TileKey(int z, int x, int y) {}
}
