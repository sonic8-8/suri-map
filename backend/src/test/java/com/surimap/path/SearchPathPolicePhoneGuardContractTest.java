package com.surimap.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.app.service.path.AppSearchPathCommandService;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.operationalperiod.testdouble.OperationalPeriodQueryMock;
import com.surimap.path.fixture.SearchPathFixtures;
import com.surimap.path.testdouble.CapturingSearchPathEventPublisher;
import com.surimap.path.testdouble.StubPolicePhoneGuard;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L4-T01 SearchPath PolicePhone guard 계약 테스트.
 *
 * <p>미등록 단말(police_phone_not_registered) / 미배정 단말(police_phone_not_assigned) 실패 흐름을 검증한다.
 */
@DisplayName("L4-T01 SearchPath PolicePhone guard contract")
class SearchPathPolicePhoneGuardContractTest {

  private AppSearchPathCommandService service;

  @BeforeEach
  void setUp() {
    service =
        new AppSearchPathCommandService(
            new OperationalPeriodQueryMock(),
            new StubPolicePhoneGuard(),
            new CapturingSearchPathEventPublisher());
  }

  @Test
  @DisplayName("미등록 단말이면 police_phone_not_registered 예외가 발생한다")
  void 미등록_단말이면_police_phone_not_registered_예외가_발생한다() {
    StartSearchPathServiceRequest request =
        new StartSearchPathServiceRequest(
            SearchPathFixtures.INCIDENT_ID,
            SearchPathFixtures.OP1_ID,
            UUID.randomUUID(),
            Instant.now(),
            null);

    assertThatThrownBy(() -> service.start(request))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            e ->
                assertThat(((SearchPathGuardException) e).errorCode())
                    .isEqualTo("police_phone_not_registered"));
  }

  @Test
  @DisplayName("등록됐으나 미배정 단말이면 police_phone_not_assigned 예외가 발생한다")
  void 등록됐으나_미배정_단말이면_police_phone_not_assigned_예외가_발생한다() {
    // StubPolicePhoneGuard: fixture phone is only assigned to OP1_ID.
    // Call guard directly — service fires op_guard first, making it impossible to reach
    // phone_guard with a mismatched OP via the service path.
    var guard = new StubPolicePhoneGuard();

    assertThatThrownBy(
            () -> guard.requireAssigned(SearchPathFixtures.POLICE_PHONE_ID, UUID.randomUUID()))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            e ->
                assertThat(((SearchPathGuardException) e).errorCode())
                    .isEqualTo("police_phone_not_assigned"));
  }
}
