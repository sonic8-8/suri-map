package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.domain.MissingPersonRecord;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.api.service.searcharea.SearchAreaApiService;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaRow;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryResult;
import com.surimap.marker.query.MarkerView;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.service.OfflinePackageService;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("offline package manifest source integration")
class OfflinePackageManifestSourceIntegrationTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000002901");
  private static final UUID OP_ID = UUID.fromString("88888888-0000-4000-8000-000000002901");
  private static final UUID OVERALL_AREA_ID =
      UUID.fromString("bbbbbbbb-0000-4000-8000-000000002901");
  private static final UUID ASSIGNED_AREA_ID =
      UUID.fromString("cccccccc-0000-4000-8000-000000002901");
  private static final UUID MARKER_ID = UUID.fromString("55555555-0000-4000-8000-000000002901");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110003");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final Instant NOW = Instant.parse("2026-05-13T00:00:00Z");
  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

  @Autowired private OfflinePackageService service;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockBean private IncidentMapper incidentMapper;

  @MockBean private OperationalPeriodQuery operationalPeriodQuery;

  @MockBean private SearchAreaApiService searchAreaQuery;

  @MockBean private SearchAreaAssignmentQuery assignmentQuery;

  @MockBean private MarkerQuery markerQuery;

  @BeforeEach
  void reset() {
    jdbcTemplate.update("DELETE FROM offline_package_installation");
    jdbcTemplate.update("DELETE FROM offline_package_manifest");
  }

  @Test
  @DisplayName("manifest is generated from committed S1/S2/S5/S8 source rows")
  void manifestIsGeneratedFromCommittedSourceRows() {
    givenSourceRows();

    OfflinePackageManifestResponse manifest =
        service.manifest(INCIDENT_ID.toString(), POLICE_PHONE_ID.toString());

    assertThat(manifest.incident().incidentId()).isEqualTo(INCIDENT_ID.toString());
    assertThat(manifest.incident().sourceFixture()).isEqualTo("mock-112-source-s7-dynamic");
    assertThat(manifest.missingPerson().displayName()).isEqualTo("동적 실종자");
    assertThat(manifest.operationalPeriods())
        .singleElement()
        .satisfies(
            op -> {
              assertThat(op.opId()).isEqualTo(OP_ID.toString());
              assertThat(op.sequenceNumber()).isEqualTo(3);
              assertThat(op.status()).isEqualTo("ACTIVE");
            });
    assertThat(manifest.assignedAreas())
        .singleElement()
        .satisfies(
            area -> {
              assertThat(area.areaId()).isEqualTo(ASSIGNED_AREA_ID.toString());
              assertThat(area.opId()).isEqualTo(OP_ID.toString());
              assertThat(area.version()).isEqualTo(6L);
            });
    assertThat(manifest.initialMarkers())
        .singleElement()
        .satisfies(
            marker -> {
              assertThat(marker.markerId()).isEqualTo(MARKER_ID.toString());
              assertThat(marker.opId()).isEqualTo(OP_ID.toString());
              assertThat(marker.coordinate())
                  .extracting(Object::toString)
                  .containsExactly("126.957", "37.572");
            });
    assertThat(manifest.overallSearchArea().areaId()).isEqualTo(OVERALL_AREA_ID.toString());
    assertThat(manifest.packageItems())
        .extracting(OfflinePackageManifestResponse.PackageItem::itemKey)
        .contains(
            "incident:" + INCIDENT_ID,
            "missing-person:" + INCIDENT_ID,
            "op-list:" + INCIDENT_ID,
            "assigned-area:" + ASSIGNED_AREA_ID,
            "initial-marker:" + MARKER_ID,
            "overall-search-area:" + OVERALL_AREA_ID,
            "tile-manifest:" + manifest.manifestId());

    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM offline_package_manifest
                WHERE incident_id = ?::uuid
                  AND operational_period_id = ?::uuid
                  AND overall_search_area_id = ?::uuid
                """,
                Integer.class,
                INCIDENT_ID.toString(),
                OP_ID.toString(),
                OVERALL_AREA_ID.toString()))
        .isOne();
  }

  private void givenSourceRows() {
    IncidentRecord incident = new IncidentRecord();
    incident.setId(INCIDENT_ID);
    incident.setSourceIncidentId("mock-112-source-s7-dynamic");
    incident.setStatus("OPEN");
    incident.setVersion(8L);
    when(incidentMapper.findByIncidentId(INCIDENT_ID)).thenReturn(Optional.of(incident));

    MissingPersonRecord missingPerson = new MissingPersonRecord();
    missingPerson.setIncidentId(INCIDENT_ID);
    missingPerson.setDisplayName("동적 실종자");
    missingPerson.setPhotoObjectKey("photo/dynamic.jpg");
    missingPerson.setAppearanceText("검은 모자");
    missingPerson.setLastSeenLocationText("동쪽 등산로");
    missingPerson.setLastSeenAt(NOW);
    when(incidentMapper.findMissingPersonByIncidentId(INCIDENT_ID))
        .thenReturn(Optional.of(missingPerson));

    OperationalPeriodRow op =
        new OperationalPeriodRow(OP_ID, INCIDENT_ID, "ACTIVE", 3, NOW, null, "RE_SEARCH", 5L);
    when(operationalPeriodQuery.list(INCIDENT_ID)).thenReturn(List.of(op));
    when(operationalPeriodQuery.current(INCIDENT_ID)).thenReturn(Optional.empty());

    GeoJsonPolygon overallPolygon =
        polygon(
            "126.950", "37.570", "126.970", "37.570", "126.970", "37.580", "126.950", "37.580");
    when(searchAreaQuery.overallOf(INCIDENT_ID))
        .thenReturn(
            Optional.of(
                new OverallSearchAreaResult(
                    OVERALL_AREA_ID,
                    INCIDENT_ID,
                    "ACTIVE",
                    4L,
                    overallPolygon,
                    List.of(),
                    NOW)));
    SearchAreaRow assignedArea =
        new SearchAreaRow(
            ASSIGNED_AREA_ID,
            INCIDENT_ID,
            OP_ID,
            OVERALL_AREA_ID,
            "ACTIVE",
            6L,
            polygon(
                "126.955",
                "37.571",
                "126.960",
                "37.571",
                "126.960",
                "37.575",
                "126.955",
                "37.575"),
            List.of(),
            NOW,
            1L);
    when(searchAreaQuery.byOp(eq(OP_ID), any()))
        .thenReturn(new SearchAreaCollection(INCIDENT_ID, 6L, List.of(assignedArea)));
    when(assignmentQuery.byOp(OP_ID))
        .thenReturn(
            List.of(
                new SearchAreaAssignmentRow(
                    UUID.fromString("dddddddd-0000-4000-8000-000000002901"),
                    ASSIGNED_AREA_ID,
                    ACCOUNT_ID,
                    ACCOUNT_ID,
                    NOW,
                    null,
                    "ACTIVE",
                    6L)));

    when(markerQuery.byIncident(eq(INCIDENT_ID), any()))
        .thenReturn(
            new MarkerQueryResult(
                INCIDENT_ID,
                List.of(
                    new MarkerView(
                        MARKER_ID,
                        INCIDENT_ID,
                        OP_ID,
                        ACCOUNT_ID,
                        POLICE_PHONE_ID,
                        MarkerType.CLUE,
                        null,
                        MarkerSource.APP,
                        MarkerStatus.ACTIVE,
                        7L,
                        jtsPoint("126.957", "37.572"),
                        "동적 단서",
                        NOW,
                        List.of()))));
  }

  private static GeoJsonPolygon polygon(
      String x1, String y1, String x2, String y2, String x3, String y3, String x4, String y4) {
    return new GeoJsonPolygon(
        "Polygon",
        List.of(
            List.of(
                coord(x1, y1),
                coord(x2, y2),
                coord(x3, y3),
                coord(x4, y4),
                coord(x1, y1))));
  }

  private static List<BigDecimal> coord(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }

  private static org.locationtech.jts.geom.Point jtsPoint(String lon, String lat) {
    var point =
        GEOMETRY_FACTORY.createPoint(
            new Coordinate(Double.parseDouble(lon), Double.parseDouble(lat)));
    point.setSRID(4326);
    return point;
  }
}
