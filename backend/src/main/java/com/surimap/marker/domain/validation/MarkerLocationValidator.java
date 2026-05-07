package com.surimap.marker.domain.validation;

import com.surimap.maparea.query.SearchAreaQuery;
import java.util.Objects;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

/**
 * S5 marker.location 검증 skeleton.
 *
 * <p>L5-T03A red test는 이 클래스가 아직 공통 geometry rule과 {@link SearchAreaQuery#overallOf(UUID)}
 * containment를 적용하지 않는 상태를 고정한다.
 */
public class MarkerLocationValidator {

  private final SearchAreaQuery searchAreaQuery;

  /**
   * MarkerLocationValidator를 생성한다.
   *
   * @param searchAreaQuery S2가 제공하는 active overall_search_area 조회 계약
   */
  public MarkerLocationValidator(SearchAreaQuery searchAreaQuery) {
    this.searchAreaQuery = Objects.requireNonNull(searchAreaQuery, "searchAreaQuery is required.");
  }

  /**
   * marker.location을 검증한다.
   *
   * <p>GREEN 구현은 EPSG:4326 Point shape/range/precision을 검사하고, {@code
   * SearchAreaQuery.overallOf(incidentId)} 결과의 active overall_search_area 내부 여부를 확인해야 한다.
   *
   * @param incidentId 사건 ID
   * @param location marker.location Point
   */
  public void validate(UUID incidentId, Point location) {
    // RED skeleton: L5-T03B에서 공통 geometry rule과 SearchAreaQuery.overallOf containment를 구현한다.
  }
}
