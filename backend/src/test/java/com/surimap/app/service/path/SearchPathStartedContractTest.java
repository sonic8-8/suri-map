package com.surimap.app.service.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.app.service.path.AppSearchPathCommandService;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.operationalperiod.testdouble.OperationalPeriodQueryMock;
import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.domain.path.testdouble.CapturingSearchPathEventPublisher;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L4-T01 SEARCH_PATH_STARTED PublishRequest 계약 테스트.
 *
 * <p>앱 PolicePhone가 path 시작 시 search_path row와 SEARCH_PATH_STARTED payload가 같은
 * id/opId/policePhoneId/version을 가진다 (S3-1.json §tdd_red_tests
 * SearchPathStartsForCurrentPolicePhoneAndOpTest).
 */
@DisplayName("L4-T01 SEARCH_PATH_STARTED contract")
class SearchPathStartedContractTest {

  private CapturingSearchPathEventPublisher publisher;
  private AppSearchPathCommandService service;

  @BeforeEach
  void setUp() {
    publisher = new CapturingSearchPathEventPublisher();
    service = new AppSearchPathCommandService(new OperationalPeriodQueryMock(), publisher);
  }

  @Test
  @DisplayName("path 시작 시 RECORDING 상태와 version=1 이 반환된다")
  void path_시작_시_RECORDING_status와_version1이_반환된다() {
    SearchPath path = service.start(validStartRequest());

    assertThat(path.status()).isEqualTo(SearchPathStatus.RECORDING);
    assertThat(path.version()).isEqualTo(1L);
    assertThat(path.opId()).isEqualTo(SearchPathFixtures.OP1_ID);
    assertThat(path.policePhoneId()).isEqualTo(SearchPathFixtures.POLICE_PHONE_ID);
    assertThat(path.accountId()).isEqualTo(SearchPathFixtures.ACCOUNT_ID);
  }

  @Test
  @DisplayName("client-generated searchPathId가 있으면 시작 path id로 사용한다")
  void client_generated_searchPathId_is_used_for_offline_batch_correlation() {
    SearchPath path =
        service.start(
            new StartSearchPathServiceRequest(
                SearchPathFixtures.PATH_ID,
                SearchPathFixtures.INCIDENT_ID,
                SearchPathFixtures.OP1_ID,
                SearchPathFixtures.POLICE_PHONE_ID,
                SearchPathFixtures.ACCOUNT_ID,
                Instant.now(),
                "idem-path-start-client-id"));

    assertThat(path.id()).isEqualTo(SearchPathFixtures.PATH_ID);
    assertThat(publisher.captured().get(0).id()).isEqualTo(SearchPathFixtures.PATH_ID);
  }

  @Test
  @DisplayName("SEARCH_PATH_STARTED payload는 path의 id/opId/policePhoneId/version과 일치한다")
  void SEARCH_PATH_STARTED_payload는_path와_일치한다() {
    SearchPath path = service.start(validStartRequest());

    assertThat(publisher.captured()).hasSize(1);
    var payload = publisher.captured().get(0);
    assertThat(payload.eventType().name()).isEqualTo("SEARCH_PATH_STARTED");
    assertThat(payload.id()).isEqualTo(path.id());
    assertThat(payload.opId()).isEqualTo(path.opId());
    assertThat(payload.policePhoneId()).isEqualTo(path.policePhoneId());
    assertThat(payload.accountId()).isEqualTo(path.accountId());
    assertThat(payload.version()).isEqualTo(path.version());
    assertThat(payload.status()).isEqualTo(SearchPathStatus.RECORDING);
  }

  @Test
  @DisplayName("accountId가 없으면 policePhoneId로 계정을 추론하지 않고 거부한다")
  void missing_account_id_is_rejected_without_police_phone_fallback() {
    StartSearchPathServiceRequest request =
        new StartSearchPathServiceRequest(
            SearchPathFixtures.INCIDENT_ID,
            SearchPathFixtures.OP1_ID,
            SearchPathFixtures.POLICE_PHONE_ID,
            Instant.now(),
            "idem-path-start-missing-account");

    assertThatThrownBy(() -> service.start(request))
        .isInstanceOfSatisfying(
            SearchPathGuardException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo("channel_not_allowed"));
  }

  private StartSearchPathServiceRequest validStartRequest() {
    return new StartSearchPathServiceRequest(
        null,
        SearchPathFixtures.INCIDENT_ID,
        SearchPathFixtures.OP1_ID,
        SearchPathFixtures.POLICE_PHONE_ID,
        SearchPathFixtures.ACCOUNT_ID,
        Instant.now(),
        "idem-path-start-contract");
  }
}
