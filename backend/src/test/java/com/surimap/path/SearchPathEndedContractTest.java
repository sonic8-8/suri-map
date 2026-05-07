package com.surimap.path;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.app.service.path.AppSearchPathCommandService;
import com.surimap.app.service.path.request.EndSearchPathServiceRequest;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.operationalperiod.testdouble.OperationalPeriodQueryMock;
import com.surimap.path.fixture.SearchPathFixtures;
import com.surimap.path.testdouble.CapturingSearchPathEventPublisher;
import com.surimap.path.testdouble.StubPolicePhoneGuard;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L4-T01 SEARCH_PATH_ENDED PublishRequest 계약 테스트.
 *
 * <p>path 종료 시 ENDED 상태와 version=2, SEARCH_PATH_ENDED payload가 path와 일치하는지 확인한다.
 */
@DisplayName("L4-T01 SEARCH_PATH_ENDED contract")
class SearchPathEndedContractTest {

  private CapturingSearchPathEventPublisher publisher;
  private AppSearchPathCommandService service;

  @BeforeEach
  void setUp() {
    publisher = new CapturingSearchPathEventPublisher();
    service =
        new AppSearchPathCommandService(
            new OperationalPeriodQueryMock(), new StubPolicePhoneGuard(), publisher);
  }

  @Test
  @DisplayName("path 종료 시 ENDED 상태와 version=2 가 반환된다")
  void path_종료_시_ENDED_status와_version2가_반환된다() {
    SearchPath started = service.start(validStartRequest());
    SearchPath ended = service.end(started, validEndRequest());

    assertThat(ended.status()).isEqualTo(SearchPathStatus.ENDED);
    assertThat(ended.version()).isEqualTo(SearchPathFixtures.PATH_ENDED_VERSION);
    assertThat(ended.id()).isEqualTo(started.id());
  }

  @Test
  @DisplayName("SEARCH_PATH_ENDED payload는 path의 id/opId/policePhoneId/version과 일치한다")
  void SEARCH_PATH_ENDED_payload는_path와_일치한다() {
    SearchPath started = service.start(validStartRequest());
    SearchPath ended = service.end(started, validEndRequest());

    assertThat(publisher.captured()).hasSize(2);
    var payload = publisher.captured().get(1);
    assertThat(payload.eventType().name()).isEqualTo("SEARCH_PATH_ENDED");
    assertThat(payload.id()).isEqualTo(ended.id());
    assertThat(payload.opId()).isEqualTo(ended.opId());
    assertThat(payload.policePhoneId()).isEqualTo(ended.policePhoneId());
    assertThat(payload.version()).isEqualTo(ended.version());
    assertThat(payload.status()).isEqualTo(SearchPathStatus.ENDED);
  }

  private StartSearchPathServiceRequest validStartRequest() {
    return new StartSearchPathServiceRequest(
        SearchPathFixtures.INCIDENT_ID,
        SearchPathFixtures.OP1_ID,
        SearchPathFixtures.POLICE_PHONE_ID,
        Instant.now(),
        null);
  }

  private EndSearchPathServiceRequest validEndRequest() {
    return new EndSearchPathServiceRequest(Instant.now(), null);
  }
}
