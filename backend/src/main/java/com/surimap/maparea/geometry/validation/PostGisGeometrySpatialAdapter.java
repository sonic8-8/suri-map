package com.surimap.maparea.geometry.validation;

import com.surimap.maparea.geometry.validation.mapper.GeometrySpatialMapper;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** PostGIS 기반 GeometrySpatialPort 구현체 */
@Repository
public class PostGisGeometrySpatialAdapter implements GeometrySpatialPort {

  /** PostGIS 쿼리를 실행하는 MyBatis Mapper다. */
  private final GeometrySpatialMapper mapper;

  /**
   * PostGisGeometrySpatialAdapter를 생성한다.
   *
   * @param mapper 공간 검증 Mapper
   */
  public PostGisGeometrySpatialAdapter(GeometrySpatialMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "공간 검증 Mapper는 null일 수 없습니다.");
  }

  /**
   * Polygon의 실제 면적이 기준 m2 이상인지 확인한다.
   *
   * <p>EPSG:4326 geometry를 geography로 변환해서 m2 단위 면적을 계산한다.
   *
   * @param polygonWkt Polygon WKT
   * @param minimumAreaM2 최소 면적 기준 m2
   * @return 기준 이상이면 true
   */
  @Override
  public boolean isAreaAtLeastM2(String polygonWkt, BigDecimal minimumAreaM2) {
    return Boolean.TRUE.equals(mapper.isAreaAtLeastM2(polygonWkt, minimumAreaM2));
  }

  /**
   * child Polygon이 parent Polygon 내부에 완전히 포함되는지 확인한다.
   *
   * @param parentPolygonWkt parent Polygon WKT
   * @param childPolygonWkt child Polygon WKT
   * @return parent가 child를 cover하면 true
   */
  @Override
  public boolean covers(String parentPolygonWkt, String childPolygonWkt) {
    return Boolean.TRUE.equals(mapper.covers(parentPolygonWkt, childPolygonWkt));
  }

  /**
   * 두 Polygon이 면적 기준으로 겹치는지 확인한다.
   *
   * <p>ST_Intersection의 면적이 0보다 크면 실제 면적이 겹친 것으로 본다.
   *
   * @param firstPolygonWkt 첫 번째 Polygon WKT
   * @param secondPolygonWkt 두 번째 Polygon WKT
   * @return 면적이 겹치면 true
   */
  @Override
  public boolean overlapsByArea(String firstPolygonWkt, String secondPolygonWkt) {
    return Boolean.TRUE.equals(mapper.overlapsByArea(firstPolygonWkt, secondPolygonWkt));
  }
}
