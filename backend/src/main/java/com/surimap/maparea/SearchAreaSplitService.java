package com.surimap.maparea;

import com.surimap.maparea.event.SearchAreaEventPublisher;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.geometry.validation.GeometryValidationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SearchAreaSplitService {

  private static final String EVENT_TYPE = "SEARCH_AREA_CHANGED";
  private static final String TABLE_NAME = "search_area";

  private final SearchAreaEventPublisher eventPublisher;
  private final GeometryValidationService geometryValidationService;
  private final ConcurrentHashMap<UUID, AreaEntity> store = new ConcurrentHashMap<>();

  public SearchAreaSplitService(
      SearchAreaEventPublisher eventPublisher,
      GeometryValidationService geometryValidationService) {
    this.eventPublisher = eventPublisher;
    this.geometryValidationService = geometryValidationService;
  }

  public void registerArea(
      UUID id, UUID incidentId, UUID opId, GeoJsonPolygon geometry, String status, int version) {
    store.put(id, new AreaEntity(id, incidentId, opId, geometry, status, version));
  }

  public SplitResponse split(SplitCommand command) {
    AreaEntity parent = store.get(command.parentAreaId());
    if (parent == null) {
      throw new IllegalArgumentException("area not found: " + command.parentAreaId());
    }
    if (parent.version() != command.expectedVersion()) {
      throw new IllegalStateException(
          "version 충돌: expected=" + command.expectedVersion() + ", actual=" + parent.version());
    }

    String overallWkt = toWkt(parent.geometry());
    for (GeoJsonPolygon child : command.children()) {
      geometryValidationService.validateSearchAreaPolygon(child, overallWkt);
    }

    AreaEntity updatedParent =
        new AreaEntity(
            parent.id(),
            parent.incidentId(),
            parent.opId(),
            parent.geometry(),
            "CANCELLED",
            parent.version() + 1);
    store.put(updatedParent.id(), updatedParent);

    SplitParentResult parentResult =
        new SplitParentResult(
            updatedParent.id(),
            updatedParent.incidentId(),
            updatedParent.opId(),
            updatedParent.status(),
            updatedParent.version(),
            updatedParent.geometry());
    eventPublisher.publish(
        new SearchAreaEventPublisher.PublishRequest(EVENT_TYPE, TABLE_NAME, parentResult));

    List<SplitChildResult> childResults = new ArrayList<>();
    for (GeoJsonPolygon childGeometry : command.children()) {
      UUID childId = UUID.randomUUID();
      SplitChildResult childResult =
          new SplitChildResult(
              childId,
              command.parentAreaId(),
              parent.incidentId(),
              parent.opId(),
              "ACTIVE",
              1,
              childGeometry);
      childResults.add(childResult);
      eventPublisher.publish(
          new SearchAreaEventPublisher.PublishRequest(EVENT_TYPE, TABLE_NAME, childResult));
    }

    return new SplitResponse(parentResult, childResults);
  }

  private String toWkt(GeoJsonPolygon polygon) {
    StringBuilder sb = new StringBuilder("POLYGON ((");
    List<List<BigDecimal>> ring = polygon.coordinates().get(0);
    for (int i = 0; i < ring.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append(ring.get(i).get(0)).append(" ").append(ring.get(i).get(1));
    }
    sb.append("))");
    return sb.toString();
  }

  private record AreaEntity(
      UUID id, UUID incidentId, UUID opId, GeoJsonPolygon geometry, String status, int version) {}

  public record SplitCommand(
      UUID parentAreaId,
      UUID incidentId,
      UUID opId,
      List<GeoJsonPolygon> children,
      int expectedVersion) {}

  public record SplitResponse(SplitParentResult parent, List<SplitChildResult> children) {}

  public record SplitParentResult(
      UUID id, UUID incidentId, UUID opId, String status, int version, GeoJsonPolygon geometry) {}

  public record SplitChildResult(
      UUID id,
      UUID parentAreaId,
      UUID incidentId,
      UUID opId,
      String status,
      int version,
      GeoJsonPolygon geometry) {}
}
