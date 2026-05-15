package com.surimap.offlinepackage.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OverallSearchAreaTileCoverage")
class OverallSearchAreaTileCoverageTest {

  @Test
  @DisplayName("overall search area polygon is converted to covering XYZ tile coordinates")
  void overallSearchAreaPolygonIsConvertedToCoveringXyzTileCoordinates() {
    GeoJsonPolygon polygon =
        new GeoJsonPolygon(
            "Polygon",
            List.of(
                List.of(
                    point("126.904000", "35.158000"),
                    point("126.923000", "35.158000"),
                    point("126.923000", "35.173000"),
                    point("126.904000", "35.173000"),
                    point("126.904000", "35.158000"))));

    List<OverallSearchAreaTileCoverage.TileCoordinate> tiles =
        OverallSearchAreaTileCoverage.covering(polygon, "osm-local", 15, 16);

    assertThat(tiles)
        .containsExactlyElementsOf(
            tiles.stream()
                .sorted(
                    Comparator.comparingInt(OverallSearchAreaTileCoverage.TileCoordinate::z)
                        .thenComparingInt(OverallSearchAreaTileCoverage.TileCoordinate::x)
                        .thenComparingInt(OverallSearchAreaTileCoverage.TileCoordinate::y))
                .toList());
    assertThat(tiles).hasSize(20);
    assertThat(tileKeys(tiles))
        .contains(
            "osm-local/15/27935/12960",
            "osm-local/15/27936/12961",
            "osm-local/16/55870/25920",
            "osm-local/16/55873/25923");
    assertThat(tileKeys(tiles)).doesNotContain("osm-local/16/27925/12681");
  }

  private static List<String> tileKeys(List<OverallSearchAreaTileCoverage.TileCoordinate> tiles) {
    return tiles.stream()
        .map(tile -> "%s/%d/%d/%d".formatted(tile.styleId(), tile.z(), tile.x(), tile.y()))
        .toList();
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }
}
