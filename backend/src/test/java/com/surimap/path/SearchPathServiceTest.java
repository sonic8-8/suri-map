package com.surimap.path;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.path.validation.GpsPathValidator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchPathServiceTest {

  private SearchPathService service;
  private CapturingPathEventPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new CapturingPathEventPublisher();
    service = new SearchPathService(new InMemorySearchPathRepository(), publisher, new GpsPathValidator());
  }

  @Test
  void batchAppend하면_id_status_version과_PATH_APPENDED가_고정된다() {
    UUID incidentId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID opId = UUID.fromString("70000000-0000-0000-0000-000000000001");
    UUID pathId = UUID.fromString("81000000-0000-0000-0000-000000000001");
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");

    var response = service.appendBatch(request(incidentId, opId, pathId), policePhoneId);

    assertThat(response.id()).isEqualTo(pathId);
    assertThat(response.status()).isEqualTo(SearchPathStatus.RECORDING);
    assertThat(response.version()).isEqualTo(2L);
    assertThat(response.acceptedPointCount()).isEqualTo(8);
    assertThat(response.excludedPointCount()).isZero();
    assertThat(response.segments()).hasSize(2);
    assertThat(response.segments()).extracting(SearchPathSegment::movementType).containsExactly(MovementType.VEHICLE, MovementType.FOOT);

    assertThat(publisher.published()).singleElement().satisfies(
        event -> {
          assertThat(event.id()).isEqualTo(pathId);
          assertThat(event.status()).isEqualTo(SearchPathStatus.RECORDING);
          assertThat(event.version()).isEqualTo(2L);
          assertThat(event.opId()).isEqualTo(opId);
          assertThat(event.policePhoneId()).isEqualTo(policePhoneId);
        });
  }

  @Test
  void getSearchPaths는_incident_op_policePhone_filter를_적용한다() {
    UUID incidentId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID opId = UUID.fromString("70000000-0000-0000-0000-000000000001");
    UUID otherOpId = UUID.fromString("70000000-0000-0000-0000-000000000002");
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");
    UUID otherPolice = UUID.fromString("50000000-0000-0000-0000-000000000002");

    service.appendBatch(request(incidentId, opId, UUID.fromString("81000000-0000-0000-0000-000000000001")), policePhoneId);
    service.appendBatch(request(incidentId, otherOpId, UUID.fromString("81000000-0000-0000-0000-000000000002")), otherPolice);

    var byIncident = service.query(incidentId, null, null);
    var byOp = service.query(incidentId, opId, null);
    var byPolice = service.query(incidentId, null, policePhoneId);

    assertThat(byIncident.paths()).hasSize(2);
    assertThat(byOp.paths()).singleElement().satisfies(path -> assertThat(path.opId()).isEqualTo(opId));
    assertThat(byPolice.paths()).singleElement().satisfies(path -> assertThat(path.policePhoneId()).isEqualTo(policePhoneId));
  }

  private PathBatchAppendRequest request(UUID incidentId, UUID opId, UUID pathId) {
    return new PathBatchAppendRequest(
        incidentId,
        opId,
        pathId,
        List.of(
            point("gps-precinct-001", "126.956000", "37.570000", 13.5, "2026-04-28T09:00:00+09:00"),
            point("gps-precinct-002", "126.956650", "37.570180", 12.8, "2026-04-28T09:00:05+09:00"),
            point("gps-precinct-003", "126.957300", "37.570360", 11.9, "2026-04-28T09:00:10+09:00"),
            point("gps-precinct-004", "126.957850", "37.570540", 9.8, "2026-04-28T09:00:15+09:00"),
            point("gps-precinct-005", "126.958000", "37.570700", 1.6, "2026-04-28T09:00:20+09:00"),
            point("gps-precinct-006", "126.958080", "37.570880", 1.3, "2026-04-28T09:00:25+09:00"),
            point("gps-precinct-007", "126.958160", "37.571050", 1.1, "2026-04-28T09:00:30+09:00"),
            point("gps-precinct-008", "126.958250", "37.571220", 1.4, "2026-04-28T09:00:35+09:00")),
        0L);
  }

  private PathBatchPointRequest point(
      String pointId, String lon, String lat, double speed, String clientTs) {
    return new PathBatchPointRequest(
        pointId,
        new BigDecimal(lon),
        new BigDecimal(lat),
        BigDecimal.valueOf(speed),
        5,
        OffsetDateTime.parse(clientTs));
  }
}
