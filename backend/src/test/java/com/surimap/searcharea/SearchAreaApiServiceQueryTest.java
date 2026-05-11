package com.surimap.searcharea;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.api.controller.searcharea.request.CreateSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.PatchSearchAreaRequest;
import com.surimap.api.controller.searcharea.response.SearchAreaResponse;
import com.surimap.api.service.searcharea.SearchAreaApiService;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.geometry.policy.GeometryPolicy;
import com.surimap.maparea.geometry.validation.GeometryValidator;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SearchAreaApiService query source contract")
class SearchAreaApiServiceQueryTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
  private static final OffsetDateTime CLIENT_TS = OffsetDateTime.parse("2026-04-28T09:00:00+09:00");

  private final SearchAreaApiService service =
      new SearchAreaApiService(new GeometryValidator(GeometryPolicy.s2HarnessDefault()));

  @Test
  @DisplayName("exposes created overall and area rows through SearchAreaQuery")
  void exposes_created_overall_and_area_rows_through_search_area_query() {
    service.create(overallCreateRequest(), "idem-overall-query-001");
    SearchAreaResponse unitArea = service.create(unitCreateRequest(), "idem-unit-query-001");

    assertThat(service.overallOf(INCIDENT_ID))
        .isPresent()
        .get()
        .satisfies(
            row -> {
              assertThat(row.incidentId()).isEqualTo(INCIDENT_ID);
              assertThat(row.status()).isEqualTo("ACTIVE");
              assertThat(row.geometry().type()).isEqualTo("Polygon");
            });

    SearchAreaCollection collection =
        service.byIncident(
            INCIDENT_ID, new SearchAreaFilters(null, OP_ID, null, null, null, false));

    assertThat(collection.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(collection.areas())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.id()).isEqualTo(unitArea.id());
              assertThat(row.opId()).isEqualTo(OP_ID);
              assertThat(row.status()).isEqualTo("ACTIVE");
              assertThat(row.version()).isEqualTo(1L);
            });
  }

  @Test
  @DisplayName("byOp honors cancelled and minVersion filters")
  void by_op_honors_cancelled_and_min_version_filters() {
    service.create(overallCreateRequest(), "idem-overall-query-002");
    SearchAreaResponse unitArea = service.create(unitCreateRequest(), "idem-unit-query-002");
    service.patch(
        unitArea.id(),
        new PatchSearchAreaRequest(OP_ID, null, null, 1L, "CANCELLED", CLIENT_TS.plusMinutes(1)),
        "idem-unit-cancel-query-002");

    assertThat(service.byOp(OP_ID, SearchAreaFilters.empty()).areas()).isEmpty();

    SearchAreaCollection collection =
        service.byOp(OP_ID, new SearchAreaFilters(null, null, null, 2L, null, true));

    assertThat(collection.areas())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.id()).isEqualTo(unitArea.id());
              assertThat(row.status()).isEqualTo("CANCELLED");
              assertThat(row.version()).isEqualTo(2L);
            });
  }

  private static CreateSearchAreaRequest overallCreateRequest() {
    return new CreateSearchAreaRequest(
        INCIDENT_ID, null, "OVERALL", polygon("126.950000", "37.570000"), null, CLIENT_TS);
  }

  private static CreateSearchAreaRequest unitCreateRequest() {
    return new CreateSearchAreaRequest(
        INCIDENT_ID, OP_ID, "UNIT", polygon("126.951000", "37.571000"), null, CLIENT_TS);
  }

  private static GeoJsonPolygon polygon(String minLon, String minLat) {
    BigDecimal lon = new BigDecimal(minLon);
    BigDecimal lat = new BigDecimal(minLat);
    BigDecimal maxLon = lon.add(new BigDecimal("0.001000"));
    BigDecimal maxLat = lat.add(new BigDecimal("0.001000"));
    return new GeoJsonPolygon(
        "Polygon",
        List.of(
            List.of(
                List.of(lon, lat),
                List.of(maxLon, lat),
                List.of(maxLon, maxLat),
                List.of(lon, maxLat),
                List.of(lon, lat))));
  }
}
