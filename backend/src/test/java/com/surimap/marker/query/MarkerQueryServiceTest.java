package com.surimap.marker.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L5-T08 RED contract test for the S5-owned MarkerQuery.byIncident source query.
 *
 * <p>S3-2 and S8 consume this read model, but do not own its implementation.
 */
@DisplayName("L5-T08 MarkerQuery.byIncident service contract")
class MarkerQueryServiceTest {

  @Test
  @DisplayName("MarkerQuery.byIncident 포트는 incidentId와 optional filters를 입력으로 받는다")
  void markerQuery_byIncident_port_exists_with_incident_filters() throws Exception {
    Class<?> markerQuery = Class.forName("com.surimap.marker.query.MarkerQuery");
    Class<?> markerQueryFilters = Class.forName("com.surimap.marker.query.MarkerQueryFilters");

    Method byIncident = markerQuery.getMethod("byIncident", UUID.class, markerQueryFilters);

    assertThat(byIncident.getReturnType().getName())
        .isEqualTo("com.surimap.marker.query.MarkerQueryResult");
  }

  @Test
  @DisplayName("marker persistence는 byIncident filter source인 incident_id를 저장한다")
  void marker_persistence_stores_incident_id_for_byIncident() throws Exception {
    String markerMigration =
        Files.readString(Path.of("src/main/resources/db/migration/V14__create_marker.sql"));

    assertThat(markerMigration).contains("incident_id UUID NOT NULL");
  }

  @Test
  @DisplayName("MarkerView row shape는 board와 OP history가 비교할 canonical fields를 노출한다")
  void markerView_row_shape_exposes_canonical_fields() throws Exception {
    Class<MarkerView> markerView = MarkerView.class;

    assertThat(markerView.getRecordComponents())
        .extracting(component -> component.getName())
        .contains(
            "id",
            "incidentId",
            "opId",
            "accountId",
            "policePhoneId",
            "type",
            "status",
            "version",
            "location",
            "memo",
            "photoSummary");
  }
}
