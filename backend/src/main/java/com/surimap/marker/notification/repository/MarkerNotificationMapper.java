package com.surimap.marker.notification.repository;

import com.surimap.marker.notification.query.MarkerNotificationToastQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarkerNotificationMapper
    extends MarkerNotificationRepository, MarkerNotificationToastQuery {

  @Override
  int insertIfAbsent(@Param("record") MarkerNotificationRecord record);
}
