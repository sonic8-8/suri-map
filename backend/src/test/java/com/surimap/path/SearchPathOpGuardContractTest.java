package com.surimap.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.app.service.path.AppSearchPathCommandService;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
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
 * L4-T01 SearchPath OP guard 계약 테스트.
 *
 * <p>현재 OP 없음(op_required) / OP 불일치(op_mismatch) 실패 흐름을 검증한다.
 */
@DisplayName("L4-T01 SearchPath OP guard contract")
class SearchPathOpGuardContractTest {

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
  @DisplayName("현재 OP가 없으면 op_required 예외가 발생한다")
  void 현재_OP가_없으면_op_required_예외가_발생한다() {
    StartSearchPathServiceRequest request =
        new StartSearchPathServiceRequest(
            UUID.randomUUID(),
            SearchPathFixtures.OP1_ID,
            SearchPathFixtures.POLICE_PHONE_ID,
            Instant.now(),
            null);

    assertThatThrownBy(() -> service.start(request))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            e -> assertThat(((SearchPathGuardException) e).errorCode()).isEqualTo("op_required"));
  }

  @Test
  @DisplayName("요청 opId가 현재 OP와 다르면 op_mismatch 예외가 발생한다")
  void 요청_opId가_현재_OP와_다르면_op_mismatch_예외가_발생한다() {
    StartSearchPathServiceRequest request =
        new StartSearchPathServiceRequest(
            SearchPathFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP2_ID,
            SearchPathFixtures.POLICE_PHONE_ID,
            Instant.now(),
            null);

    assertThatThrownBy(() -> service.start(request))
        .isInstanceOf(SearchPathGuardException.class)
        .satisfies(
            e -> assertThat(((SearchPathGuardException) e).errorCode()).isEqualTo("op_mismatch"));
  }
}
