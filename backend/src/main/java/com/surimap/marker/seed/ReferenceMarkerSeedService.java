package com.surimap.marker.seed;

import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.marker.query.MarkerView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReferenceMarkerSeedService implements ReferenceMarkerSeed {

  private final MarkerMapper markerMapper;

  public ReferenceMarkerSeedService(MarkerMapper markerMapper) {
    this.markerMapper = Objects.requireNonNull(markerMapper, "markerMapper");
  }

  @Override
  public ReferenceMarkerSeedResult createForIncident(
      UUID incidentId, List<SeedMarker> seedMarkers) {
    Objects.requireNonNull(incidentId, "incidentId");
    Objects.requireNonNull(seedMarkers, "seedMarkers");

    List<Marker> records =
        seedMarkers.stream()
            .peek(seed -> Marker.validateLocation(seed.location()))
            .map(seed -> Marker.fromSeed(incidentId, seed))
            .toList();
    if (records.isEmpty()) {
      return new ReferenceMarkerSeedResult(incidentId, List.of());
    }

    records.forEach(markerMapper::insertSeed);

    List<UUID> markerIds = records.stream().map(Marker::getId).toList();
    Map<UUID, Marker> persisted =
        markerMapper.findByIds(markerIds).stream()
            .collect(Collectors.toMap(Marker::getId, Function.identity()));
    List<MarkerView> markers =
        markerIds.stream()
            .map(persisted::get)
            .filter(Objects::nonNull)
            .map(marker -> marker.toView(List.of()))
            .toList();
    return new ReferenceMarkerSeedResult(incidentId, markers);
  }
}
