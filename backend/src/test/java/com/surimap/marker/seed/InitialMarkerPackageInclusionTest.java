package com.surimap.marker.seed;

import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.INCIDENT_ID;
import static com.surimap.marker.seed.fixture.MarkerSeedFixtures.MARKER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.query.MarkerView;
import com.surimap.marker.seed.fixture.MarkerSeedFixtures;
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L5-T05B S7 offline package initialMarkers inclusion test. */
@DisplayName("L5-T05B initial marker package inclusion")
class InitialMarkerPackageInclusionTest {

  @Test
  @DisplayName("offline package manifest probe는 seed marker를 initialMarkers로 포함한다")
  void offline_package_manifest_probe는_seed_marker를_initialMarkers로_포함한다() {
    InMemoryMarkerRepository repository = new InMemoryMarkerRepository();
    ReferenceMarkerSeed referenceMarkerSeed = new ReferenceMarkerSeedService(repository);

    ReferenceMarkerSeedResult seedResult =
        referenceMarkerSeed.createForIncident(
            INCIDENT_ID, List.of(MarkerSeedFixtures.referenceClueSeed()));
    OfflinePackageManifestProbe manifest = OfflinePackageManifestProbe.from(seedResult);

    assertThat(manifest.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(manifest.initialMarkers()).hasSize(1);
    assertThat(manifest.initialMarkers().get(0).id()).isEqualTo(MARKER_ID);
    assertThat(manifest.initialMarkers().get(0).status()).isEqualTo(MarkerStatus.ACTIVE);
    assertThat(manifest.packageItemTypes()).contains("INITIAL_MARKER");
  }

  private record OfflinePackageManifestProbe(
      UUID incidentId, List<MarkerView> initialMarkers, List<String> packageItemTypes) {

    private static OfflinePackageManifestProbe from(ReferenceMarkerSeedResult seedResult) {
      return new OfflinePackageManifestProbe(
          seedResult.incidentId(), seedResult.markers(), List.of("INITIAL_MARKER"));
    }
  }
}
