package com.surimap.incident.repository;

import com.surimap.incident.domain.Mock112WebhookEventRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface Mock112WebhookEventMapper {

  Optional<Mock112WebhookEventRecord> findByEventId(@Param("eventId") String eventId);

  void insertReserved(
      @Param("eventId") String eventId,
      @Param("eventType") String eventType,
      @Param("sourceIncidentId") UUID sourceIncidentId,
      @Param("requestBodyHash") String requestBodyHash,
      @Param("now") Instant now);

  void complete(
      @Param("eventId") String eventId,
      @Param("incidentId") UUID incidentId,
      @Param("incidentStatus") String incidentStatus,
      @Param("incidentVersion") long incidentVersion,
      @Param("responseBodyJson") String responseBodyJson,
      @Param("now") Instant now);
}
