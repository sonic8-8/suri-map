package com.surimap.incident.repository;

import com.surimap.incident.domain.IncidentImportIdempotencyRecord;
import com.surimap.incident.domain.IncidentRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Incident import 흐름이 사용하는 MyBatis mapper. incident, missing_person, incident_assignment,
 * idempotency_record 4개 테이블에 대한 write·조회 SQL을 호출한다. 실제 SQL 본문은 {@code
 * mapper/incident/IncidentMapper.xml}에 정의된다.
 */
@Mapper
public interface IncidentMapper {

  Optional<IncidentRecord> findBySourceIncidentId(
      @Param("sourceIncidentId") String sourceIncidentId);

  Optional<IncidentImportIdempotencyRecord> findImportIdempotencyRecord(
      @Param("idempotencyKey") String idempotencyKey,
      @Param("requestPath") String requestPath,
      @Param("requestMethod") String requestMethod);

  void insertImportIdempotencyRecord(
      @Param("id") UUID id,
      @Param("idempotencyKey") String idempotencyKey,
      @Param("requestBodyHash") String requestBodyHash,
      @Param("requestPath") String requestPath,
      @Param("requestMethod") String requestMethod,
      @Param("idempotencyStatus") String idempotencyStatus,
      @Param("now") Instant now);

  void completeImportIdempotencyRecord(
      @Param("idempotencyKey") String idempotencyKey,
      @Param("requestPath") String requestPath,
      @Param("requestMethod") String requestMethod,
      @Param("responseStatusCode") int responseStatusCode,
      @Param("responseBodyJson") String responseBodyJson,
      @Param("resultEntityId") UUID resultEntityId,
      @Param("resultEntityStatus") String resultEntityStatus,
      @Param("resultEntityVersion") long resultEntityVersion,
      @Param("now") Instant now);

  void insertIncident(
      @Param("id") UUID id,
      @Param("sourceIncidentId") String sourceIncidentId,
      @Param("title") String title,
      @Param("status") String status,
      @Param("openedAt") Instant openedAt,
      @Param("version") long version,
      @Param("now") Instant now);

  void insertMissingPerson(
      @Param("incidentId") UUID incidentId,
      @Param("displayName") String displayName,
      @Param("photoObjectKey") String photoObjectKey,
      @Param("appearanceText") String appearanceText,
      @Param("lastSeenLocationText") String lastSeenLocationText,
      @Param("lastSeenAt") Instant lastSeenAt,
      @Param("importedAt") Instant importedAt);

  void insertIncidentAssignment(
      @Param("id") UUID id,
      @Param("incidentId") UUID incidentId,
      @Param("accountId") String accountId,
      @Param("incidentRole") String incidentRole,
      @Param("assignedAt") Instant assignedAt,
      @Param("now") Instant now);

  List<String> findActiveAssignmentAccountIds(@Param("incidentId") UUID incidentId);
}
