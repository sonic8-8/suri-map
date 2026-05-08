package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.app.service.policephone.AppPolicePhoneHeartbeatService;
import com.surimap.app.service.policephone.request.PolicePhoneHeartbeatServiceRequest;
import com.surimap.eventhub.adapter.MockEventHub;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T03 AppPolicePhoneHeartbeatService")
class AppPolicePhoneHeartbeatServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-08T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryPolicePhoneFixtureStore fixtureStore = new InMemoryPolicePhoneFixtureStore(clock);
  private final MockEventHub eventHub = new MockEventHub();
  private final AppPolicePhoneHeartbeatService service =
      new AppPolicePhoneHeartbeatService(fixtureStore, eventHub, clock);

  @Test
  @DisplayName("lower sequence heartbeat is ignored without extra publish")
  void lowerSequenceHeartbeatIgnoredWithoutExtraPublish() {
    var accepted = service.heartbeat(request(3L, Instant.parse("2026-05-08T00:00:00Z")));
    var ignored = service.heartbeat(request(2L, Instant.parse("2026-05-08T00:00:10Z")));

    assertThat(accepted.accepted()).isTrue();
    assertThat(ignored.accepted()).isFalse();
    assertThat(ignored.sequence()).isEqualTo(3L);
    assertThat(ignored.version()).isEqualTo(1L);
    assertThat(eventHub.findByType(PolicePhoneHeartbeatUpdatedPublishRequest.TYPE)).hasSize(1);
  }

  private PolicePhoneHeartbeatServiceRequest request(long sequence, Instant lastSyncAt) {
    return new PolicePhoneHeartbeatServiceRequest(
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
        PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID,
        PolicePhoneFixtures.ASSIGNED_ACCOUNT_TYPE,
        PolicePhoneFixtures.ASSIGNED_ORGANIZATION_TYPE,
        clock.instant(),
        sequence,
        lastSyncAt,
        85);
  }
}
