package com.surimap.domain.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MarkerNotificationTest {

  @Test
  @DisplayName("알림 생성 후 원본 수신자 목록이 바뀌어도, 생성 당시 계정·업무폰 목록을 유지한다")
  void build_afterRecipientListsChange_preservesRecipientsAtCreation() {
    // given: 외부에서 수정할 수 있는 계정·업무폰 목록을 준비한다.
    String accountId = UUID.randomUUID().toString();
    String policePhoneId = UUID.randomUUID().toString();
    List<String> accountIds = new ArrayList<>(List.of(accountId));
    List<String> policePhoneIds = new ArrayList<>(List.of(policePhoneId));

    // when: 알림을 생성한 뒤 원본 목록을 비운다.
    MarkerNotification notification =
        createNotificationBuilder()
            .recipientAccountIds(accountIds)
            .recipientPolicePhoneIds(policePhoneIds)
            .build();
    accountIds.clear();
    policePhoneIds.clear();

    // then: 생성 당시 수신자를 보존하고, getter로 받은 목록도 직접 수정할 수 없다.
    assertThat(notification.getRecipientAccountIds()).containsExactly(accountId);
    assertThat(notification.getRecipientPolicePhoneIds()).containsExactly(policePhoneId);
    assertThatThrownBy(() -> notification.getRecipientAccountIds().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> notification.getRecipientPolicePhoneIds().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("필수값이 하나라도 없으면, 알림 생성을 거부하고 누락된 필드를 알린다")
  void build_missingRequiredValue_rejectsCreation() {
    // given: 유효한 알림 데이터에서 필수값을 하나씩 제거한다.
    // when & then: 기존 필수값 검사와 오류 메시지를 유지한다.
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().id(null).build())
        .withMessage("id must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().markerId(null).build())
        .withMessage("markerId must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().notificationType(null).build())
        .withMessage("notificationType must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().recipientRule(null).build())
        .withMessage("recipientRule must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().recipientAccountIds(null).build())
        .withMessage("recipientAccountIds must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().recipientPolicePhoneIds(null).build())
        .withMessage("recipientPolicePhoneIds must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().notificationPayloadJson(null).build())
        .withMessage("notificationPayloadJson must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().status(null).build())
        .withMessage("status must not be null");
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().createdAt(null).build())
        .withMessage("createdAt must not be null");
  }

  @ParameterizedTest(name = "알림 버전: {0}")
  @ValueSource(longs = {0, -1})
  @DisplayName("버전이 0 이하이면, 알림 생성을 거부한다")
  void build_nonPositiveVersion_rejectsCreation(long version) {
    // given: 필수값은 모두 있지만 버전이 유효하지 않은 알림을 준비한다.
    MarkerNotification.MarkerNotificationBuilder builder =
        createNotificationBuilder().version(version);

    // when & then: 양수가 아닌 버전으로 알림을 만들 수 없다.
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("version must be positive");
  }

  @Test
  @DisplayName("수신자 목록에 null이 포함되어 있으면, 알림 생성을 거부한다")
  void build_nullRecipient_rejectsCreation() {
    // given: 목록 자체는 있지만 수신자 식별자가 빠져 있다.
    List<String> recipients = Collections.singletonList(null);

    // when & then: 계정 목록과 업무폰 목록 모두 null 항목을 허용하지 않는다.
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().recipientAccountIds(recipients).build());
    assertThatNullPointerException()
        .isThrownBy(() -> createNotificationBuilder().recipientPolicePhoneIds(recipients).build());
  }

  private MarkerNotification.MarkerNotificationBuilder createNotificationBuilder() {
    return MarkerNotification.builder()
        .id(UUID.randomUUID())
        .markerId(UUID.randomUUID())
        .notificationType(MarkerNotificationType.SUPPORT_REQUEST_CREATED)
        .recipientRule(MarkerNotificationRecipientPolicy.COMMANDERS_AND_FIELD_COMMANDERS)
        .recipientAccountIds(List.of())
        .recipientPolicePhoneIds(List.of())
        .notificationPayloadJson("{}")
        .status(MarkerNotificationStatus.SNAPSHOT_CREATED)
        .version(1L)
        .createdAt(Instant.parse("2026-09-11T00:00:00Z"));
  }
}
