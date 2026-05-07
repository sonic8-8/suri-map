package com.surimap.incident.testdouble;

import com.surimap.incident.fixture.IncidentSeedFixtureIds;
import com.surimap.incident.fixture.IncidentSeedFixtureIds.SeedMarker;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** S5 {@code ReferenceMarkerSeed.createForIncident} capture mock. */
public final class MockReferenceMarkerSeedAdapter {

  private final IncidentSeedFixtureIds seed;
  private final List<CreatedMarkers> createdMarkers = new ArrayList<>();

  public MockReferenceMarkerSeedAdapter(IncidentSeedFixtureIds seed) {
    this.seed = Objects.requireNonNull(seed, "seed는 null일 수 없습니다");
  }

  public void createForIncident(String incidentId, List<SeedMarker> seedMarkers) {
    if (!seed.incidentId().equals(incidentId)) {
      throw new IllegalArgumentException("fixture에 없는 incidentId입니다: " + incidentId);
    }
    if (!seed.seedMarkers().equals(seedMarkers)) {
      throw new IllegalArgumentException("seedMarkers가 mock-112 원본 답안표와 일치하지 않습니다");
    }
    createdMarkers.add(new CreatedMarkers(incidentId, seedMarkers));
  }

  public List<CreatedMarkers> createdMarkers() {
    return List.copyOf(createdMarkers);
  }

  public record CreatedMarkers(String incidentId, List<SeedMarker> seedMarkers) {

    public CreatedMarkers {
      seedMarkers = List.copyOf(seedMarkers);
    }
  }
}
