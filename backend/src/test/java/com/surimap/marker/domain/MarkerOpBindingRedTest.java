package com.surimap.marker.domain;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ALIAS;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ALIAS;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP2_ALIAS;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP2_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP_MISMATCH_MARKER_ALIAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.surimap.marker.domain.exception.OpMismatchException;
import com.surimap.marker.domain.exception.OpRequiredException;
import com.surimap.marker.domain.port.OperationalPeriodQueryPort;
import com.surimap.marker.domain.validation.MarkerOpBindingValidator;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** L5-T03A marker create current OP binding red test. */
@DisplayName("L5-T03A marker OP binding red test")
class MarkerOpBindingRedTest {

  private final OperationalPeriodQueryPort currentOp1Query = incidentId -> Optional.of(OP1_ID);
  private final OperationalPeriodQueryPort noCurrentOpQuery = incidentId -> Optional.empty();
  private final OperationalPeriodQueryPort currentOp2Query = incidentId -> Optional.of(OP2_ID);

  @Nested
  @DisplayName("fixture exactness")
  class FixtureExactness {

    @Test
    @DisplayName("OP binding fixture alias를 보존한다")
    void op_binding_fixture_alias를_보존한다() {
      assertThat(INCIDENT_ALIAS).isEqualTo("inc-precinct-first-001");
      assertThat(OP1_ALIAS).isEqualTo("op-precinct-001-op1");
      assertThat(OP2_ALIAS).isEqualTo("op-precinct-001-op2");
      assertThat(OP_MISMATCH_MARKER_ALIAS).isEqualTo("mk-precinct-op-mismatch-001");
    }
  }

  @Nested
  @DisplayName("valid OP binding")
  class ValidBinding {

    @Test
    @DisplayName("request.opId가 current OP와 같으면 통과한다")
    void request_opId가_current_op와_같으면_통과한다() {
      MarkerOpBindingValidator validator = new MarkerOpBindingValidator(currentOp1Query);

      assertDoesNotThrow(() -> validator.validate(INCIDENT_ID, OP1_ID));
    }
  }

  @Nested
  @DisplayName("invalid OP binding")
  class InvalidBinding {

    @Test
    @DisplayName("current OP가 없으면 op_required다")
    void current_op가_없으면_op_required다() {
      MarkerOpBindingValidator validator = new MarkerOpBindingValidator(noCurrentOpQuery);

      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, OP1_ID))
          .isInstanceOfSatisfying(
              OpRequiredException.class, ex -> assertThat(ex.errorCode()).isEqualTo("op_required"));
    }

    @Test
    @DisplayName("request.opId가 current OP와 다르면 op_mismatch다")
    void request_opId가_current_op와_다르면_op_mismatch다() {
      MarkerOpBindingValidator validator = new MarkerOpBindingValidator(currentOp2Query);

      assertThatThrownBy(() -> validator.validate(INCIDENT_ID, OP1_ID))
          .isInstanceOfSatisfying(
              OpMismatchException.class, ex -> assertThat(ex.errorCode()).isEqualTo("op_mismatch"));
    }
  }
}
