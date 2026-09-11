package com.surimap.domain.marker;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarkerNotificationMapper {

  int insertIfAbsent(@Param("notification") MarkerNotification notification);

  List<NotificationRow> findNotificationRowsByIncidentId(@Param("incidentId") UUID incidentId);

  /** 알림에 마커 정보와 최근 이벤트 ID를 합친 SQL 조회 결과다. */
  @Getter
  @Builder
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  class NotificationRow {
    private UUID notificationId;
    private UUID markerId;
    private UUID incidentId;
    private UUID opId;
    private UUID policePhoneId;
    private String notificationType;
    private String status;
    private long version;
    private Instant createdAt;
    private UUID latestEventId;
  }
}
