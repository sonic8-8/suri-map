package com.surimap.app.service.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.api.controller.path.SearchPathController;
import com.surimap.api.controller.path.SearchPathSegmentController;
import com.surimap.api.controller.path.request.PathBatchAppendRequest;
import com.surimap.api.controller.path.request.PathBatchPointRequest;
import com.surimap.api.controller.path.request.PathSegmentCorrectionRequest;
import com.surimap.api.controller.path.response.PathBatchAppendResponse;
import com.surimap.api.controller.path.response.PathSegmentCorrectionResponse;
import com.surimap.api.service.path.CapturingPathEventPublisher;
import com.surimap.api.service.path.SearchPathService;
import com.surimap.app.service.path.AppSearchPathCommandService;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.InMemorySearchPathRepository;
import com.surimap.domain.path.MovementType;
import com.surimap.operationalperiod.testdouble.OperationalPeriodQueryMock;
import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.domain.path.testdouble.CapturingSearchPathEventPublisher;
import com.surimap.domain.path.validation.GpsPathValidator;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("S3-1 search path durable idempotency")
class SearchPathIdempotencyIntegrationTest {

  private static final Instant STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private IdempotentResponseCache idempotentResponseCache;

  private ObjectProvider<IdempotentResponseCache> cacheProvider;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("TRUNCATE TABLE idempotency_record");
    cacheProvider = new FixedObjectProvider<>(idempotentResponseCache);
  }

  @Test
  @DisplayName("path start replay uses idempotency_record without publishing twice")
  void startReplayUsesDurableRecord() {
    CapturingSearchPathEventPublisher publisher = new CapturingSearchPathEventPublisher();
    AppSearchPathCommandService service = appCommandService(publisher);
    StartSearchPathServiceRequest request = startRequest("idem-s3-path-start-db");

    SearchPath created = service.start(request);
    SearchPath replayed = appCommandService(publisher).start(request);

    assertThat(replayed).isEqualTo(created);
    assertThat(publisher.captured()).hasSize(1);
    assertThat(idempotencyStatus("idem-s3-path-start-db")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("same path start key with different body is rejected")
  void startSameKeyDifferentBodyIsRejected() {
    AppSearchPathCommandService service =
        appCommandService(new CapturingSearchPathEventPublisher());
    service.start(startRequest("idem-s3-path-start-mismatch"));

    assertThatThrownBy(
            () ->
                service.start(
                    new StartSearchPathServiceRequest(
                        null,
                        SearchPathFixtures.INCIDENT_ID,
                        SearchPathFixtures.OP1_ID,
                        SearchPathFixtures.POLICE_PHONE_ID,
                        SearchPathFixtures.ACCOUNT_ID,
                        STARTED_AT.plusSeconds(1),
                        "idem-s3-path-start-mismatch")))
        .isInstanceOf(IdempotencyMismatchException.class);
  }

  @Test
  @DisplayName("batch append replay does not append points or publish twice")
  void batchAppendReplayUsesDurableRecord() {
    CapturingPathEventPublisher publisher = new CapturingPathEventPublisher();
    SearchPathController controller = searchPathController(publisher);

    PathBatchAppendResponse first =
        appendBatchThroughController(controller, "idem-s3-path-batch-db", batchRequest());
    PathBatchAppendResponse replayed =
        appendBatchThroughController(controller, "idem-s3-path-batch-db", batchRequest());

    assertThat(replayed).isEqualTo(first);
    assertThat(first.geometry()).hasSize(8);
    assertThat(publisher.published()).hasSize(1);
    assertThat(idempotencyStatus("idem-s3-path-batch-db")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("segment correction replay does not increment version or publish twice")
  void segmentCorrectionReplayUsesDurableRecord() {
    CapturingPathEventPublisher publisher = new CapturingPathEventPublisher();
    SearchPathService service = searchPathService(publisher);
    SearchPathController pathController = new SearchPathController(service, cacheProvider);
    SearchPathSegmentController segmentController =
        new SearchPathSegmentController(service, cacheProvider);
    PathBatchAppendResponse batch =
        appendBatchThroughController(
            pathController, "idem-s3-path-batch-before-segment", batchRequest());
    String segmentId = batch.segments().get(0).id();
    PathSegmentCorrectionRequest request =
        new PathSegmentCorrectionRequest(MovementType.FOOT, "manual correction");

    PathSegmentCorrectionResponse first =
        segmentController
            .correctSegment(
                segmentId,
                "30000000-0000-0000-0000-000000000001",
                "idem-s3-path-segment-db",
                request)
            .getBody();
    PathSegmentCorrectionResponse replayed =
        segmentController
            .correctSegment(
                segmentId,
                "30000000-0000-0000-0000-000000000001",
                "idem-s3-path-segment-db",
                request)
            .getBody();

    assertThat(replayed).isEqualTo(first);
    assertThat(first.version()).isEqualTo(2L);
    assertThat(publisher.segmentUpdated()).hasSize(1);
    assertThat(idempotencyStatus("idem-s3-path-segment-db")).isEqualTo("COMPLETED");
  }

  private AppSearchPathCommandService appCommandService(
      CapturingSearchPathEventPublisher publisher) {
    return new AppSearchPathCommandService(
        new OperationalPeriodQueryMock(), publisher, null, cacheProvider);
  }

  private SearchPathController searchPathController(CapturingPathEventPublisher publisher) {
    return new SearchPathController(searchPathService(publisher), cacheProvider);
  }

  private SearchPathService searchPathService(CapturingPathEventPublisher publisher) {
    return new SearchPathService(
        new InMemorySearchPathRepository(), publisher, new GpsPathValidator());
  }

  private PathBatchAppendResponse appendBatchThroughController(
      SearchPathController controller, String idempotencyKey, PathBatchAppendRequest request) {
    var previousContext = SecurityContextHolder.getContext();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new SuriMapAuthentication(
            SearchPathFixtures.ACCOUNT_ID.toString(),
            AccountType.PATROL_CAR,
            OrganizationType.POLICE_SUBSTATION,
            Channel.APP,
            SearchPathFixtures.POLICE_PHONE_ID.toString(),
            List.of(new SimpleGrantedAuthority(Role.MEMBER.name()))));
    try {
      SecurityContextHolder.setContext(context);
      return controller
          .appendBatch(SearchPathFixtures.POLICE_PHONE_ID.toString(), idempotencyKey, request)
          .getBody();
    } finally {
      SecurityContextHolder.setContext(previousContext);
    }
  }

  private StartSearchPathServiceRequest startRequest(String idempotencyKey) {
    return new StartSearchPathServiceRequest(
        null,
        SearchPathFixtures.INCIDENT_ID,
        SearchPathFixtures.OP1_ID,
        SearchPathFixtures.POLICE_PHONE_ID,
        SearchPathFixtures.ACCOUNT_ID,
        STARTED_AT,
        idempotencyKey);
  }

  private PathBatchAppendRequest batchRequest() {
    return new PathBatchAppendRequest(
        SearchPathFixtures.INCIDENT_ID,
        SearchPathFixtures.OP1_ID,
        SearchPathFixtures.PATH_ID,
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

  private String idempotencyStatus(String idempotencyKey) {
    return jdbcTemplate.queryForObject(
        """
        SELECT idempotency_status
        FROM idempotency_record
        WHERE idempotency_key = ?
        """,
        String.class,
        idempotencyKey);
  }

  private record FixedObjectProvider<T>(T value) implements ObjectProvider<T> {
    @Override
    public T getObject(Object... args) {
      return value;
    }

    @Override
    public T getObject() {
      return value;
    }

    @Override
    public T getIfAvailable() {
      return value;
    }

    @Override
    public T getIfUnique() {
      return value;
    }

    @Override
    public Iterator<T> iterator() {
      return List.of(value).iterator();
    }

    @Override
    public Stream<T> stream() {
      return Stream.of(value);
    }

    @Override
    public Stream<T> orderedStream() {
      return stream();
    }
  }
}
