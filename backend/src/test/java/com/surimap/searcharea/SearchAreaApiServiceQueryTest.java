package com.surimap.searcharea;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.api.controller.searcharea.request.AssignSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.CreateSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.PatchSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.SplitSearchAreaRequest;
import com.surimap.api.controller.searcharea.response.SearchAreaResponse;
import com.surimap.api.controller.searcharea.response.SearchAreaSplitResponse;
import com.surimap.api.service.searcharea.SearchAreaApiService;
import com.surimap.eventhub.adapter.MockEventHub;
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
  private static final UUID OTHER_OP_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
  private static final UUID ASSIGNEE_ID = UUID.fromString("11111111-1111-1111-1111-111111111001");
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

  @Test
  @DisplayName("overall create and patch publish SEARCH_AREA_CHANGED without opId")
  void overall_create_and_patch_publish_search_area_changed_without_op_id() {
    MockEventHub eventHub = new MockEventHub();
    SearchAreaApiService publishingService =
        new SearchAreaApiService(
            new GeometryValidator(GeometryPolicy.s2HarnessDefault()), eventHub);

    SearchAreaResponse overall =
        publishingService.create(overallCreateRequest(), "idem-overall-publish-query-004");
    publishingService.patch(
        overall.id(),
        new PatchSearchAreaRequest(
            null,
            polygon("126.912000", "35.163000"),
            "overall geometry update",
            1L,
            null,
            CLIENT_TS.plusMinutes(2)),
        "idem-overall-patch-publish-query-004");

    assertThat(eventHub.findByType("SEARCH_AREA_CHANGED"))
        .hasSize(2)
        .allSatisfy(
            event -> {
              assertThat(event.payload()).containsEntry("incidentId", INCIDENT_ID.toString());
              assertThat(event.payload()).containsKey("overallAreaHash");
              assertThat(event.payload()).doesNotContainKey("opId");
            });
  }

  @Test
  @DisplayName("UNIT split publishes changed events for cancelled parent and TEAM children")
  void unit_split_publishes_search_area_changed_for_parent_and_children() {
    MockEventHub eventHub = new MockEventHub();
    SearchAreaApiService publishingService =
        new SearchAreaApiService(
            new GeometryValidator(GeometryPolicy.s2HarnessDefault()), eventHub);
    publishingService.create(overallCreateRequest(), "idem-overall-publish-query-005");
    SearchAreaResponse unit =
        publishingService.create(unitCreateRequest(), "idem-unit-publish-query-005");
    eventHub.reset();

    SearchAreaSplitResponse split =
        publishingService.split(
            unit.id(),
            new SplitSearchAreaRequest(
                OP_ID,
                List.of(
                    polygon("126.911100", "35.161100"),
                    polygon("126.911500", "35.161100")),
                "unit split",
                1L,
                CLIENT_TS.plusMinutes(3)),
            "idem-unit-split-publish-query-005");

    assertThat(eventHub.findByType("SEARCH_AREA_CHANGED"))
        .hasSize(3)
        .extracting(event -> event.payload().get("id"))
        .containsExactlyInAnyOrderElementsOf(
            List.of(
                split.parent().id().toString(),
                split.children().get(0).id().toString(),
                split.children().get(1).id().toString()));
    assertThat(eventHub.findByType("SEARCH_AREA_CHANGED"))
        .allSatisfy(
            event -> {
              assertThat(event.payload()).containsEntry("incidentId", INCIDENT_ID.toString());
              assertThat(event.payload()).containsEntry("opId", OP_ID.toString());
              assertThat(event.payload()).containsKey("geometry");
              assertThat(event.payload()).doesNotContainKey("overallAreaHash");
            });
  }

  @Test
  @DisplayName("assign publishes SEARCH_AREA_ASSIGNMENT_CHANGED with assignee ids")
  void assign_publishes_search_area_assignment_changed() {
    MockEventHub eventHub = new MockEventHub();
    SearchAreaApiService publishingService =
        new SearchAreaApiService(
            new GeometryValidator(GeometryPolicy.s2HarnessDefault()), eventHub);
    publishingService.create(overallCreateRequest(), "idem-overall-publish-query-006");
    SearchAreaResponse unit =
        publishingService.create(unitCreateRequest(), "idem-unit-publish-query-006");
    eventHub.reset();

    publishingService.assign(
        unit.id(),
        new AssignSearchAreaRequest(
            INCIDENT_ID, OP_ID, List.of(ASSIGNEE_ID), "unit assignment", CLIENT_TS.plusMinutes(4)),
        "idem-unit-assign-publish-query-006");

    assertThat(eventHub.findByType("SEARCH_AREA_ASSIGNMENT_CHANGED"))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.sourceEntityType()).isEqualTo("search_area_assignment");
              assertThat(event.payload()).containsEntry("incidentId", INCIDENT_ID.toString());
              assertThat(event.payload()).containsEntry("opId", OP_ID.toString());
              assertThat(event.payload()).containsEntry("searchAreaId", unit.id().toString());
              assertThat(event.payload()).containsEntry("status", "ACTIVE");
              assertThat(event.payload()).containsEntry("version", 2L);
              assertThat(event.payload().get("assignedAccountIds"))
                  .isEqualTo(List.of(ASSIGNEE_ID.toString()));
            });
  }

  @Test
  @DisplayName("overall split uses the requested OP when the parent overall belongs to another OP")
  void overall_split_uses_requested_op_for_children_when_parent_overall_belongs_to_another_op() {
    SearchAreaResponse overall = service.create(overallCreateRequest(OP_ID), "idem-overall-query-003");

    SearchAreaSplitResponse response =
        service.split(
            overall.id(),
            new SplitSearchAreaRequest(
                OTHER_OP_ID,
                List.of(
                    polygon("126.910100", "35.160100"),
                    polygon("126.910500", "35.160100")),
                "overall split",
                1L,
                CLIENT_TS.plusMinutes(21)),
            "idem-overall-split-query-003");

    assertThat(response.parent().status()).isEqualTo("ACTIVE");
    assertThat(response.children())
        .hasSize(2)
        .allSatisfy(child -> assertThat(child.opId()).isEqualTo(OTHER_OP_ID));
    assertThat(service.byOp(OTHER_OP_ID, SearchAreaFilters.empty()).areas())
        .hasSize(2)
        .allSatisfy(row -> assertThat(row.opId()).isEqualTo(OTHER_OP_ID));
  }

  private static CreateSearchAreaRequest overallCreateRequest() {
    return new CreateSearchAreaRequest(
        INCIDENT_ID, null, "OVERALL", polygon("126.910000", "35.162000"), null, CLIENT_TS);
  }

  private static CreateSearchAreaRequest overallCreateRequest(UUID opId) {
    return new CreateSearchAreaRequest(
        INCIDENT_ID, opId, "OVERALL", polygon("126.910000", "35.162000"), null, CLIENT_TS);
  }

  private static CreateSearchAreaRequest unitCreateRequest() {
    return new CreateSearchAreaRequest(
        INCIDENT_ID, OP_ID, "UNIT", polygon("126.911000", "35.161000"), null, CLIENT_TS);
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
