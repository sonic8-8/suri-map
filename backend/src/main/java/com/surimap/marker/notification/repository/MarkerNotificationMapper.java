package com.surimap.marker.notification.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarkerNotificationMapper extends MarkerNotificationRepository {

  @Override
  int insertIfAbsent(@Param("record") MarkerNotificationRecord record);
}
