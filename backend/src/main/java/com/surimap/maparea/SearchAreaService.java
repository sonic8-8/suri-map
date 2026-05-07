package com.surimap.maparea;

import com.surimap.maparea.event.SearchAreaEventPublisher;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.geometry.validation.GeometryValidationService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S2 수색 구역 생성·수정 서비스.
 *
 * <p>geometry 검증 후 in-memory store에 저장하고, SEARCH_AREA_CHANGED 이벤트를 발행한다.
 *
 * <p>기준 문서: docs/spec/specs/S2.json
 */
public class SearchAreaService {

  private static final String EVENT_TYPE = "SEARCH_AREA_CHANGED";
  private static final String TABLE_NAME = "search_area";

  private final SearchAreaEventPublisher eventPublisher;
  private final GeometryValidationService geometryValidationService;
  private final Map<UUID, SearchAreaEntity> store = new ConcurrentHashMap<>();

  public SearchAreaService(
      SearchAreaEventPublisher eventPublisher,
      GeometryValidationService geometryValidationService) {
    this.eventPublisher = eventPublisher;
    this.geometryValidationService = geometryValidationService;
  }

  /**
   * 수색 구역을 생성한다.
   *
   * <p>geometry 검증 후 ACTIVE 상태로 생성하고 SEARCH_AREA_CHANGED 이벤트를 발행한다.
   *
   * @param command 생성 커맨드
   * @return 생성된 수색 구역 응답
   */
  public SearchAreaResponse create(CreateCommand command) {
    geometryValidationService.validateSearchAreaPolygon(
        command.polygon(), command.overallAreaWkt());

    UUID id = UUID.randomUUID();
    int version = 1;
    SearchAreaEntity entity =
        new SearchAreaEntity(
            id, command.incidentId(), command.opId(), command.polygon(), "ACTIVE", version);
    store.put(id, entity);

    eventPublisher.publish(
        new SearchAreaEventPublisher.PublishRequest(
            EVENT_TYPE, TABLE_NAME, Map.of("searchAreaId", id.toString())));

    return new SearchAreaResponse(id, "ACTIVE", version);
  }

  /**
   * 수색 구역을 수정한다.
   *
   * <p>geometry 검증 후 version을 증가시키고 SEARCH_AREA_CHANGED 이벤트를 발행한다.
   *
   * @param command 수정 커맨드
   * @return 수정된 수색 구역 응답
   */
  public SearchAreaResponse update(UpdateCommand command) {
    geometryValidationService.validateSearchAreaPolygon(
        command.polygon(), command.overallAreaWkt());

    SearchAreaEntity existing = store.get(command.id());
    if (existing == null) {
      throw new IllegalArgumentException("존재하지 않는 수색 구역입니다: " + command.id());
    }

    if (existing.version() != command.expectedVersion()) {
      throw new IllegalStateException(
          "version 충돌: expected=" + command.expectedVersion() + ", actual=" + existing.version());
    }

    int newVersion = existing.version() + 1;
    SearchAreaEntity updated =
        new SearchAreaEntity(
            existing.id(),
            existing.incidentId(),
            existing.opId(),
            command.polygon(),
            existing.status(),
            newVersion);
    store.put(existing.id(), updated);

    eventPublisher.publish(
        new SearchAreaEventPublisher.PublishRequest(
            EVENT_TYPE, TABLE_NAME, Map.of("searchAreaId", existing.id().toString())));

    return new SearchAreaResponse(existing.id(), existing.status(), newVersion);
  }

  // -----------------------------------------------------------------------
  // Commands
  // -----------------------------------------------------------------------

  /**
   * 수색 구역 생성 커맨드.
   *
   * @param incidentId 사건 ID
   * @param opId OP ID
   * @param polygon 수색 구역 GeoJSON Polygon
   * @param overallAreaWkt active overall_search_area WKT
   */
  public record CreateCommand(
      UUID incidentId, UUID opId, GeoJsonPolygon polygon, String overallAreaWkt) {}

  /**
   * 수색 구역 수정 커맨드.
   *
   * @param id 수색 구역 ID
   * @param polygon 수정할 GeoJSON Polygon
   * @param overallAreaWkt active overall_search_area WKT
   * @param expectedVersion 낙관적 잠금용 기대 버전
   */
  public record UpdateCommand(
      UUID id, GeoJsonPolygon polygon, String overallAreaWkt, int expectedVersion) {}

  // -----------------------------------------------------------------------
  // Response
  // -----------------------------------------------------------------------

  /**
   * 수색 구역 응답.
   *
   * @param id 수색 구역 ID
   * @param status 상태 (ACTIVE 등)
   * @param version 현재 버전
   */
  public record SearchAreaResponse(UUID id, String status, int version) {}

  // -----------------------------------------------------------------------
  // Internal entity (in-memory)
  // -----------------------------------------------------------------------

  private record SearchAreaEntity(
      UUID id, UUID incidentId, UUID opId, GeoJsonPolygon polygon, String status, int version) {}
}
