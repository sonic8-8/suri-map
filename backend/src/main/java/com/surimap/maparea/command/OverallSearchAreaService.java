package com.surimap.maparea.command;

import com.surimap.maparea.command.exception.AreaStateConflictException;
import com.surimap.maparea.command.request.CreateOverallSearchAreaServiceRequest;
import com.surimap.maparea.command.request.UpdateOverallSearchAreaServiceRequest;
import com.surimap.maparea.command.response.OverallSearchAreaCommandResponse;
import com.surimap.maparea.event.SearchAreaEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * overall_search_area 생성·수정 service.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §acceptance_criteria AC-S2-01. missing_area, blindspot,
 * recommended_area 관련 메서드는 포함하지 않는다 (FR-23 scope excluded).
 */
public class OverallSearchAreaService {

  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String STATUS_CANCELLED = "CANCELLED";
  private static final String EVENT_SEARCH_AREA_CHANGED = "SEARCH_AREA_CHANGED";
  private static final String TABLE_OVERALL_SEARCH_AREA = "overall_search_area";

  private final SearchAreaEventPublisher eventPublisher;

  /** in-memory active store: incidentId -> current ACTIVE row (same id across updates) */
  private final List<OverallSearchAreaRow> store = new ArrayList<>();

  /** cancelled history rows (id is preserved, status=CANCELLED) */
  private final List<OverallSearchAreaRow> history = new ArrayList<>();

  public OverallSearchAreaService(SearchAreaEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /**
   * overall_search_area를 생성한다.
   *
   * <p>같은 incident에 ACTIVE row가 이미 있으면 {@link AreaStateConflictException}을 던진다.
   */
  public OverallSearchAreaCommandResponse create(CreateOverallSearchAreaServiceRequest request) {
    long activeCount = countActiveOverallAreas(request.incidentId());
    if (activeCount > 0) {
      throw new AreaStateConflictException(
          "ACTIVE overall_search_area already exists for incident: " + request.incidentId());
    }

    UUID id = UUID.randomUUID();
    long version = 1L;
    OverallSearchAreaRow row =
        new OverallSearchAreaRow(id, request.incidentId(), STATUS_ACTIVE, version);
    store.add(row);

    eventPublisher.recordMutatedTable(TABLE_OVERALL_SEARCH_AREA);
    eventPublisher.publish(
        EVENT_SEARCH_AREA_CHANGED,
        Set.of("id", "incidentId", "status", "version", "geometry", "serverTs"));

    return new OverallSearchAreaCommandResponse(id, request.incidentId(), STATUS_ACTIVE, version);
  }

  /**
   * overall_search_area를 수정한다.
   *
   * <p>entity ID를 유지한 채 version을 증가시키고 이전 버전 row를 CANCELLED history로 보존한다. S2.json §state_machines
   * 허용 상태: ACTIVE|COMPLETED|CANCELLED.
   */
  public OverallSearchAreaCommandResponse update(UpdateOverallSearchAreaServiceRequest request) {
    OverallSearchAreaRow existing = findActiveById(request.overallSearchAreaId());

    // 이전 버전을 CANCELLED history로 보존 (id 동일, version 동일, status=CANCELLED)
    history.add(
        new OverallSearchAreaRow(
            existing.id, existing.incidentId, STATUS_CANCELLED, existing.version));

    // 동일 ID로 version만 증가 (entity identity 유지)
    long newVersion = existing.version + 1;
    existing.version = newVersion;

    eventPublisher.recordMutatedTable(TABLE_OVERALL_SEARCH_AREA);
    eventPublisher.publish(
        EVENT_SEARCH_AREA_CHANGED,
        Set.of("id", "incidentId", "status", "version", "geometry", "serverTs"));

    return new OverallSearchAreaCommandResponse(
        existing.id, request.incidentId(), STATUS_ACTIVE, newVersion);
  }

  /** 해당 incident의 ACTIVE overall_search_area 수를 반환한다. */
  public long countActiveOverallAreas(UUID incidentId) {
    return store.stream()
        .filter(r -> r.incidentId.equals(incidentId) && STATUS_ACTIVE.equals(r.status))
        .count();
  }

  /** 해당 incident의 CANCELLED(대체됨) overall_search_area history 수를 반환한다. */
  public long countSupersededOverallAreas(UUID incidentId) {
    return history.stream().filter(r -> r.incidentId.equals(incidentId)).count();
  }

  private OverallSearchAreaRow findActiveById(UUID id) {
    return store.stream()
        .filter(r -> r.id.equals(id) && STATUS_ACTIVE.equals(r.status))
        .findFirst()
        .orElseThrow(() -> new AreaStateConflictException("No ACTIVE row found for id: " + id));
  }

  /** in-memory row representation. */
  private static final class OverallSearchAreaRow {
    final UUID id;
    final UUID incidentId;
    String status;
    long version;

    OverallSearchAreaRow(UUID id, UUID incidentId, String status, long version) {
      this.id = id;
      this.incidentId = incidentId;
      this.status = status;
      this.version = version;
    }
  }
}
