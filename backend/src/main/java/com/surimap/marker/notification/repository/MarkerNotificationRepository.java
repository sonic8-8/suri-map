package com.surimap.marker.notification.repository;

public interface MarkerNotificationRepository {

  int insertIfAbsent(MarkerNotificationRecord record);
}
