package com.surimap.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathEventType;
import com.surimap.eventhub.adapter.MockEventHub;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SearchPath event publisher")
class SearchPathEventPublisherTest {

  private static final UUID PATH_ID = UUID.fromString("21000000-0000-0000-0000-000000000001");
  private static final UUID INCIDENT_ID =
      UUID.fromString("21000000-0000-0000-0000-000000000002");
  private static final UUID OP_ID = UUID.fromString("21000000-0000-0000-0000-000000000003");
  private static final UUID ACCOUNT_ID =
      UUID.fromString("21000000-0000-0000-0000-000000000005");

  @Test
  @DisplayName("데이터베이스 환경 확인 없이 수색 경로 이벤트를 EventHub에 전달한다")
  void publishesWithoutDatabaseEnvironmentCheck() {
    MockEventHub eventHub = new MockEventHub();
    SearchPathEventPublisher publisher = new SearchPathEventPublisher(eventHub);
    SearchPath path =
        SearchPath.builder()
            .id(PATH_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .accountId(ACCOUNT_ID)
            .build();

    publisher.publishLifecycle(path, SearchPathEventType.SEARCH_PATH_STARTED);

    assertThat(eventHub.findByType("SEARCH_PATH_STARTED"))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.incidentId()).isEqualTo(INCIDENT_ID);
              assertThat(event.sourceEntityId()).isEqualTo(PATH_ID);
              assertThat(event.payload()).containsEntry("accountId", ACCOUNT_ID.toString());
              assertThat(event.payload()).doesNotContainKey("policePhoneId");
            });
  }
}
