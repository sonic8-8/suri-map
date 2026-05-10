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

    assertThat(tileManifest.manifestId()).isEqualTo("tile-manifest-inc-precinct-001");
    assertThat(tileManifest.styleId()).isEqualTo("osm-local");
    assertThat(tileManifest.overallAreaHash()).isEqualTo("overall-area-hash-precinct-current");
    assertThat(tileManifest.blobUriTemplate())
        .isEqualTo("local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf");

    assertThat(tileManifest.tileKeyRange().minZ()).isEqualTo(15);
    assertThat(tileManifest.tileKeyRange().maxZ()).isEqualTo(16);
    assertThat(tileManifest.tileKeyRange().minX()).isEqualTo(27925);
    assertThat(tileManifest.tileKeyRange().maxX()).isEqualTo(27960);
    assertThat(tileManifest.tileKeyRange().minY()).isEqualTo(12680);
    assertThat(tileManifest.tileKeyRange().maxY()).isEqualTo(12720);
  }

  @Test
  @DisplayName("tile items are local-only and preserve exact key checksum byte triples")
  void tile_items_are_local_only_and_exact() {
    assertThat(OfflinePackageManifestFixtures.tileManifest().tiles())
        .containsExactly(
            new TileItem(
                "tile:osm-local:15:27925:12680",
                "osm-local",
                15,
                27925,
                12680,
                "local://tiles/inc-precinct-first-001/15/27925/12680.pbf",
                "sha256:354260e6043ab9b70662016952da6cdc783319deae611490d0803d5b417b000c",
                18432),
            new TileItem(
                "tile:osm-local:15:27926:12680",
                "osm-local",
                15,
                27926,
                12680,
                "local://tiles/inc-precinct-first-001/15/27926/12680.pbf",
                "sha256:ffa729767ab0dd0add127c19b0b1243f553dadaf7f796a593d180d00552ea977",
                20480),
            new TileItem(
                "tile:osm-local:16:27925:12681",
                "osm-local",
                16,
                27925,
                12681,
                "local://tiles/inc-precinct-first-001/16/27925/12681.pbf",
                "sha256:64fc20008bd026acb2cc672812de4fa0f1928fc763f894c82c5ef89c2beb6165",
                24576));

    assertThat(OfflinePackageManifestFixtures.tileManifest().tiles())
        .allSatisfy(
            tile -> {
              assertThat(tile.styleId()).isEqualTo("osm-local");
              assertThat(OfflinePackageManifestFixtures.isLocalTileUri(tile.url())).isTrue();
              assertThat(tile.z()).isBetween(15, 16);
              assertThat(tile.x()).isBetween(27925, 27960);
              assertThat(tile.y()).isBetween(12680, 12720);
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
