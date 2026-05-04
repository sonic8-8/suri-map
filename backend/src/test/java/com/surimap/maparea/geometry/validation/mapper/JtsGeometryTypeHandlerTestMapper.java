package com.surimap.maparea.geometry.validation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.locationtech.jts.geom.Polygon;

import java.util.UUID;

/**
 * JTS Geometry TypeHandler 검증용 test mapper.
 */
@Mapper
public interface JtsGeometryTypeHandlerTestMapper {

    void insert(
            @Param("id") UUID id,
            @Param("incidentId") UUID incidentId,
            @Param("opId") UUID opId,
            @Param("accountId") UUID accountId,
            @Param("geometry") Polygon geometry
    );

    GeometryProbeRow findById(@Param("id") UUID id);

    class GeometryProbeRow {
        private UUID id;
        private Polygon geometry;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public Polygon getGeometry() {
            return geometry;
        }

        public void setGeometry(Polygon geometry) {
            this.geometry = geometry;
        }
    }
}
