package com.surimap.global.event;

import static com.surimap.marker.notification.fixture.NotificationFixtures.PERSON_FOUND_NOTIFICATION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MarkerEventIdsTest {

  @ParameterizedTest(name = "이벤트 유형: {0}")
  @CsvSource({
    "PERSON_FOUND,efc55939-2b22-3752-b6f9-84e072ecc53a",
    "SUPPORT_REQUEST_CREATED,95df6971-9d58-3fce-a0a8-0432be29ebd0"
  })
  @DisplayName("같은 알림 ID·유형·버전으로 계산하면, 기존 규칙의 이벤트 ID를 반환한다")
  void eventId_sameNotification_returnsStableEventId(String eventType, String expectedEventId) {
    // given: 기존 전송 테스트에서 사용한 알림 ID와 미리 계산한 이벤트 ID를 유지한다.
    UUID notificationId = UUID.fromString(PERSON_FOUND_NOTIFICATION_ID);

    // when: 지원 요청·발견 알림의 최초 버전에 해당하는 이벤트 ID를 계산한다.
    UUID eventId = MarkerEventIds.eventId(eventType, notificationId, 1L);

    // then: 운영 코드로 기대값을 다시 계산하지 않고 고정된 값과 비교한다.
    assertThat(eventId).isEqualTo(UUID.fromString(expectedEventId));
  }
}
