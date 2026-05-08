package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.incident.lifecycle.IncidentLifecycleGuardException;
import com.surimap.incident.lifecycle.IncidentLifecycleQuery;
import com.surimap.incident.lifecycle.IncidentLifecycleSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("L1-T02 사건 lifecycle guard 정책")
class IncidentLifecycleGuardPolicyTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");

  private final FakeIncidentLifecycleQuery query = new FakeIncidentLifecycleQuery();
  private final IncidentLifecycleGuard guard = new IncidentLifecycleGuard(query);

  @Test
  @DisplayName("OPEN 사건은 write guard를 통과하고 상태와 version을 보존한다")
  void openIncidentAllowsDomainWrite() {
    IncidentLifecycleSnapshot open = new IncidentLifecycleSnapshot(INCIDENT_ID, "OPEN", 1L);
    query.next = Optional.of(open);

    IncidentLifecycleSnapshot actual = guard.requireOpen(INCIDENT_ID);

    assertThat(actual).isEqualTo(open);
  }

  @Test
  @DisplayName("CLOSED 사건은 409 incident_closed로 거부한다")
  void closedIncidentRejectsDomainWriteWithIncidentClosed() {
    query.next = Optional.of(new IncidentLifecycleSnapshot(INCIDENT_ID, "CLOSED", 3L));

    assertThatThrownBy(() -> guard.requireOpen(INCIDENT_ID))
        .isInstanceOfSatisfying(
            IncidentLifecycleGuardException.class,
            exception -> {
              assertThat(exception.error()).isEqualTo("incident_closed");
              assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
            });
  }

  @Test
  @DisplayName("OPEN 전 상태는 409 incident_bootstrapping으로 거부한다")
  void bootstrappingIncidentRejectsDomainWriteWithIncidentBootstrapping() {
    query.next = Optional.of(new IncidentLifecycleSnapshot(INCIDENT_ID, "BOOTSTRAPPING", 1L));

    assertThatThrownBy(() -> guard.requireOpen(INCIDENT_ID))
        .isInstanceOfSatisfying(
            IncidentLifecycleGuardException.class,
            exception -> {
              assertThat(exception.error()).isEqualTo("incident_bootstrapping");
              assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
            });
  }

  private static final class FakeIncidentLifecycleQuery implements IncidentLifecycleQuery {

    private Optional<IncidentLifecycleSnapshot> next = Optional.empty();

    @Override
    public Optional<IncidentLifecycleSnapshot> findByIncidentId(UUID incidentId) {
      return next;
    }
  }
}
