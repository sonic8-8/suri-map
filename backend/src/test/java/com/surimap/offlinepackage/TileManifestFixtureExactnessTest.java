package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.TileItem;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.TileManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L6-T05 SC-03 tile manifest exactness test. */
@DisplayName("L6-T05 SC-03 tile manifest fixture exactness")
class TileManifestFixtureExactnessTest {

  @Test
  @DisplayName("tile manifest identity, range, and blob URI template match harness constants")
  void tile_manifest_identity_range_and_blob_template_match_harness_constants() {
    TileManifest tileManifest = OfflinePackageManifestFixtures.tileManifest();

    assertThat(tileManifest.manifestId()).isEqualTo(OfflinePackageManifestFixtures.MANIFEST_ID);
    assertThat(tileManifest.styleId()).isEqualTo("osm-local");
    assertThat(tileManifest.overallAreaHash()).isEqualTo("overall-area-hash-precinct-current");
    assertThat(tileManifest.blobUriTemplate())
        .isEqualTo("local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf");

    assertThat(tileManifest.tileKeyRange().minZ()).isEqualTo(15);
    assertThat(tileManifest.tileKeyRange().maxZ()).isEqualTo(16);
    assertThat(tileManifest.tileKeyRange().minX()).isEqualTo(27935);
    assertThat(tileManifest.tileKeyRange().maxX()).isEqualTo(55873);
    assertThat(tileManifest.tileKeyRange().minY()).isEqualTo(12960);
    assertThat(tileManifest.tileKeyRange().maxY()).isEqualTo(25923);
  }

  @Test
  @DisplayName("tile items are local-only and preserve exact key checksum byte triples")
  void tile_items_are_local_only_and_exact() {
    assertThat(OfflinePackageManifestFixtures.tileManifest().tiles())
        .containsExactly(
            new TileItem(
                "tile:osm-local:15:27935:12960",
                "osm-local",
                15,
                27935,
                12960,
                "local://tiles/inc-precinct-first-001/15/27935/12960.pbf",
                "sha256:1631a7b03c6924b5f966d85597afc394c602dd1fa214efc3e6ea0d666817b9fe",
                18432),
            new TileItem(
                "tile:osm-local:15:27936:12960",
                "osm-local",
                15,
                27936,
                12960,
                "local://tiles/inc-precinct-first-001/15/27936/12960.pbf",
                "sha256:3012bcff12c3416907e05ead8236a213f5067da1c00f44c014fea5a803f46c05",
                20480),
            new TileItem(
                "tile:osm-local:16:55870:25920",
                "osm-local",
                16,
                55870,
                25920,
                "local://tiles/inc-precinct-first-001/16/55870/25920.pbf",
                "sha256:f9965c1686fa1d08356d3fee122b95cbbe9b2f76f1764108c4bf48e31f00cd60",
                24576));

    assertThat(OfflinePackageManifestFixtures.tileManifest().tiles())
        .allSatisfy(
            tile -> {
              assertThat(tile.styleId()).isEqualTo("osm-local");
              assertThat(OfflinePackageManifestFixtures.isLocalTileUri(tile.url())).isTrue();
              assertThat(tile.z()).isBetween(15, 16);
              assertThat(tile.x()).isBetween(27935, 55873);
              assertThat(tile.y()).isBetween(12960, 25923);
            });
  }

  @Test
  @DisplayName("failure injection keys match SC-03 and SC-04 tile fixture")
  void failure_injection_keys_match_harness_fixture() {
    assertThat(OfflinePackageManifestFixtures.tileManifest().failureKeys())
        .containsExactlyInAnyOrder(
            "manifest-expired",
            "manifest-overall-area-stale",
            "tile-404",
            "tile-timeout",
            "tile-checksum-mismatch",
            "tile-corrupt-blob");
  }

  @Test
  @DisplayName("external OSM Mapbox and Google tile URLs are rejected by fixture contract")
  void external_tile_hosts_are_rejected() {
    assertThat(OfflinePackageManifestFixtures.EXTERNAL_TILE_URLS)
        .allSatisfy(
            url ->
                assertThat(OfflinePackageManifestFixtures.isExternalTileUrlRejected(url))
                    .as(url)
                    .isTrue());
  }
}
