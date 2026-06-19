package com.surimap.path;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.path.validation.GpsPathValidator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

  @Test
  void getSearchPaths는_전체경로를_읽지_않고_repository_조건조회로_위임한다() {
    UUID incidentId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID opId = UUID.fromString("70000000-0000-0000-0000-000000000001");
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");
    UUID accountId = UUID.fromString("30000000-0000-0000-0000-000000000001");
    QueryBoundaryRepository repository = new QueryBoundaryRepository();
    SearchPathService queryService =
        new SearchPathService(repository, publisher, new GpsPathValidator());

    PathQueryResponse response = queryService.query(incidentId, opId, policePhoneId, accountId);

    assertThat(response.paths()).isEmpty();
    assertThat(repository.queriedIncidentId).isEqualTo(incidentId);
    assertThat(repository.queriedOpId).isEqualTo(opId);
    assertThat(repository.queriedPolicePhoneId).isEqualTo(policePhoneId);
    assertThat(repository.queriedAccountId).isEqualTo(accountId);
  }

  @Test
  void getSearchPaths는_segment_geometry와_시간을_제공한다() {
    UUID incidentId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID opId = UUID.fromString("70000000-0000-0000-0000-000000000001");
    UUID pathId = UUID.fromString("81000000-0000-0000-0000-000000000001");
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");

    service.appendBatch(request(incidentId, opId, pathId), policePhoneId);

    PathQueryRow path = service.query(incidentId, opId, policePhoneId).paths().get(0);

    assertThat(path.startedAt()).isEqualTo(OffsetDateTime.parse("2026-04-28T09:00:00+09:00").toInstant());
    assertThat(path.endedAt()).isNull();
    assertThat(path.segments())
        .extracting(PathQuerySegmentRow::movementType)
        .containsExactly(MovementType.VEHICLE, MovementType.FOOT);
    assertThat(path.segments().get(0).geometry())
        .containsExactly(
            List.of(126.913, 35.162),
            List.of(126.91365, 35.16218),
            List.of(126.9143, 35.16236),
            List.of(126.91485, 35.16254));
    assertThat(path.segments().get(0).startedAt())
        .isEqualTo(OffsetDateTime.parse("2026-04-28T09:00:00+09:00"));
    assertThat(path.segments().get(0).endedAt())
        .isEqualTo(OffsetDateTime.parse("2026-04-28T09:00:15+09:00"));
  }

  @Test
  void 연속3개미만_speed_run은_UNKNOWN으로_분류한다() {
    UUID incidentId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID opId = UUID.fromString("70000000-0000-0000-0000-000000000001");
    UUID pathId = UUID.fromString("81000000-0000-0000-0000-000000000001");
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");

    var response =
        service.appendBatch(
            new PathBatchAppendRequest(
                incidentId,
                opId,
                pathId,
                List.of(
                    point("p1", "126.913000", "35.162000", 12.0, "2026-04-28T09:00:00+09:00"),
                    point("p2", "126.913200", "35.162050", 12.0, "2026-04-28T09:00:05+09:00"),
                    point("p3", "126.913300", "35.162100", 1.2, "2026-04-28T09:00:10+09:00"),
                    point("p4", "126.913400", "35.162150", 1.2, "2026-04-28T09:00:15+09:00")),
                0L),
            policePhoneId);

    assertThat(response.segments())
        .allSatisfy(segment -> assertThat(segment.movementType()).isEqualTo(MovementType.UNKNOWN));
  }

  @Test
  void segment_manual_correction은_movementType만_수정하고_SEARCH_PATH_SEGMENT_UPDATED를_발행한다() {
    UUID incidentId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID opId = UUID.fromString("70000000-0000-0000-0000-000000000001");
    UUID pathId = UUID.fromString("81000000-0000-0000-0000-000000000001");
    UUID policePhoneId = UUID.fromString("50000000-0000-0000-0000-000000000001");
    UUID accountId = UUID.fromString("30000000-0000-0000-0000-000000000001");

    var appended = service.appendBatch(request(incidentId, opId, pathId), policePhoneId);
    var target = appended.segments().get(0);

    var corrected = service.correctSegment(target.id(), MovementType.FOOT, accountId);

    assertThat(corrected.segment().id()).isEqualTo(target.id());
    assertThat(corrected.segment().startIndex()).isEqualTo(target.startIndex());
    assertThat(corrected.segment().endIndex()).isEqualTo(target.endIndex());
    assertThat(corrected.segment().startPointId()).isEqualTo(target.startPointId());
    assertThat(corrected.segment().endPointId()).isEqualTo(target.endPointId());
    assertThat(corrected.segment().movementType()).isEqualTo(MovementType.FOOT);
    assertThat(corrected.segment().movementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);
    assertThat(corrected.segment().correctedByAccountId()).isEqualTo(accountId);
    assertThat(corrected.opId()).isEqualTo(opId);
    assertThat(corrected.policePhoneId()).isEqualTo(policePhoneId);

    assertThat(publisher.segmentUpdated())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.id()).isEqualTo(pathId);
              assertThat(event.status()).isEqualTo(SearchPathStatus.RECORDING);
              assertThat(event.opId()).isEqualTo(opId);
              assertThat(event.policePhoneId()).isEqualTo(policePhoneId);
              assertThat(event.segmentId()).isEqualTo(target.id());
              assertThat(event.movementType()).isEqualTo(MovementType.FOOT);
              assertThat(event.movementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);
            });
  }

  private PathBatchAppendRequest request(UUID incidentId, UUID opId, UUID pathId) {
    return new PathBatchAppendRequest(
        incidentId,
        opId,
        pathId,
        List.of(
            point("gps-precinct-001", "126.913000", "35.162000", 13.5, "2026-04-28T09:00:00+09:00"),
            point("gps-precinct-002", "126.913650", "35.162180", 12.8, "2026-04-28T09:00:05+09:00"),
            point("gps-precinct-003", "126.914300", "35.162360", 11.9, "2026-04-28T09:00:10+09:00"),
            point("gps-precinct-004", "126.914850", "35.162540", 9.8, "2026-04-28T09:00:15+09:00"),
            point("gps-precinct-005", "126.915000", "35.162700", 1.6, "2026-04-28T09:00:20+09:00"),
            point("gps-precinct-006", "126.915080", "35.162880", 1.3, "2026-04-28T09:00:25+09:00"),
            point("gps-precinct-007", "126.915160", "35.163050", 1.1, "2026-04-28T09:00:30+09:00"),
            point("gps-precinct-008", "126.915250", "35.163120", 1.4, "2026-04-28T09:00:35+09:00")),
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

  private static final class QueryBoundaryRepository implements SearchPathRepository {

    private UUID queriedIncidentId;
    private UUID queriedOpId;
    private UUID queriedPolicePhoneId;
    private UUID queriedAccountId;

    @Override
    public Optional<SearchPathAggregate> findById(UUID pathId) {
      return Optional.empty();
    }

    @Override
    public SearchPathAggregate save(SearchPathAggregate aggregate) {
      return aggregate;
    }

    @Override
    public List<SearchPathAggregate> findAll() {
      throw new AssertionError("SearchPathService.query must not load every path");
    }

    @Override
    public List<SearchPathAggregate> findByQuery(
        UUID incidentId, UUID opId, UUID policePhoneId, UUID accountId) {
      this.queriedIncidentId = incidentId;
      this.queriedOpId = opId;
      this.queriedPolicePhoneId = policePhoneId;
      this.queriedAccountId = accountId;
      return List.of();
    }
  }
}
