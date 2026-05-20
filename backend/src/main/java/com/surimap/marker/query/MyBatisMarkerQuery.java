package com.surimap.marker.query;

import com.surimap.marker.repository.MarkerMapper;
import com.surimap.marker.repository.MarkerPhotoSummaryRow;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.photo.port.ObjectStoragePort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyBatisMarkerQuery implements MarkerQuery {

  private final MarkerMapper markerMapper;
  private final ObjectProvider<ObjectStoragePort> objectStoragePort;

  public MyBatisMarkerQuery(MarkerMapper markerMapper, ObjectProvider<ObjectStoragePort> objectStoragePort) {
    this.markerMapper = markerMapper;
    this.objectStoragePort = objectStoragePort;
  }

  @Override
  @Transactional(readOnly = true)
  public MarkerQueryResult byIncident(UUID incidentId, MarkerQueryFilters filters) {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    MarkerQueryFilters effectiveFilters = filters == null ? MarkerQueryFilters.empty() : filters;

    List<MarkerRecord> markerRecords = markerMapper.findByIncident(incidentId, effectiveFilters);
    if (markerRecords.isEmpty()) {
      return new MarkerQueryResult(incidentId, List.of());
    }

    Map<UUID, List<MarkerPhotoSummary>> photoSummaryByMarkerId =
        attachedPhotoSummaryByMarkerId(markerRecords);
    List<MarkerView> markers =
        markerRecords.stream()
            .map(
                record ->
                    record.toView(photoSummaryByMarkerId.getOrDefault(record.getId(), List.of())))
            .toList();
    return new MarkerQueryResult(incidentId, markers);
  }

  private Map<UUID, List<MarkerPhotoSummary>> attachedPhotoSummaryByMarkerId(
      List<MarkerRecord> markerRecords) {
    List<UUID> markerIds = markerRecords.stream().map(MarkerRecord::getId).toList();
    List<MarkerPhotoSummaryRow> photoSummaries =
        markerMapper.findAttachedPhotoSummariesByMarkerIds(markerIds);

    Map<UUID, List<MarkerPhotoSummary>> grouped = new HashMap<>();
    for (MarkerPhotoSummaryRow row : photoSummaries) {
      grouped
          .computeIfAbsent(row.markerId(), ignored -> new java.util.ArrayList<>())
          .add(row.toSummary(viewUrl(row)));
    }
    return grouped;
  }

  private String viewUrl(MarkerPhotoSummaryRow row) {
    ObjectStoragePort storagePort = objectStoragePort == null ? null : objectStoragePort.getIfAvailable();
    if (storagePort == null) {
      return null;
    }
    try {
      return storagePort
          .generatePresignedViewUrl(row.objectKey(), ObjectStoragePort.DEFAULT_VIEW_TTL)
          .orElse(null);
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
