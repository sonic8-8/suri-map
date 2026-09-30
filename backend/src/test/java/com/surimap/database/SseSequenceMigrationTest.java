package com.surimap.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.global.event.EventPublishRequest;
import com.surimap.global.sse.ServerSentEventJob;
import com.surimap.global.sse.ServerSentEventJobMapper;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "spring.flyway.target=20260721.001")
class SseSequenceMigrationTest extends PostGisIntegrationTestSupport {

  @Autowired private DataSource dataSource;
  @Autowired private IncidentMapper incidentMapper;
  @Autowired private ServerSentEventJobMapper jobMapper;

  @Test
  @DisplayName("순번·저장 순서 컬럼을 추가해도 기존 작업·원본·확정 순번은 보존하고 과거 순서는 추정하지 않는다")
  void migrations_preserve_existing_jobs_source_data_and_assigned_sequences() {
    // given: 직전 migration까지 적용한 DB에 원본과 상태가 서로 다른 전송 작업이 있다.
    UUID incidentId = UUID.randomUUID();
    UUID markerId = UUID.randomUUID();
    UUID pathId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-26T00:00:00Z");
    incidentMapper.insertIncident(
        incidentId, UUID.randomUUID(), "Existing incident", "OPEN", occurredAt, 7L, occurredAt);
    insertSourceData(incidentId, markerId, pathId);
    List<String> statuses = List.of("COMPLETED", "PENDING", "FAILED", "DISPATCHING");
    List<UUID> jobIds = new ArrayList<>();
    for (String status : statuses) {
      ServerSentEventJob job =
          ServerSentEventJob.from(
              EventPublishRequest.builder()
                  .eventId(UUID.randomUUID())
                  .incidentId(incidentId)
                  .type("PERSON_FOUND")
                  .payloadFormatVersion(1)
                  .sourceEntityType("marker")
                  .sourceEntityId(markerId)
                  .occurredAt(occurredAt)
                  .payload(Map.of("id", markerId.toString(), "status", "ACTIVE", "version", 1))
                  .build());
      jobMapper.insert(job);
      jdbcTemplate.update(
          "UPDATE event_dispatch_job SET dispatch_status = ? WHERE id = ?", status, job.getId());
      jobIds.add(job.getId());
    }
    List<String> jobsBefore = readJobContents(incidentId);
    List<String> sourcesBefore = readSourceContents(markerId, pathId);

    // when: 실제 main Flyway migration으로 SSE 순번 컬럼을 추가한다.
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target("20260926.001")
        .load()
        .migrate();

    // then: 기존 내용·상태·업무 버전은 그대로이며 순번만 미배정 상태로 추가된다.
    assertThat(readJobContents(incidentId)).isEqualTo(jobsBefore);
    assertThat(readSourceContents(markerId, pathId)).isEqualTo(sourcesBefore);
    assertThat(incidentMapper.findByIncidentId(incidentId).orElseThrow().getVersion())
        .isEqualTo(7L);
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isZero();
    List<ServerSentEventJob> migrated = jobIds.stream().map(jobMapper::findById).toList();
    assertThat(migrated)
        .extracting(ServerSentEventJob::getDispatchStatus)
        .containsExactlyElementsOf(statuses);
    assertThat(migrated).allSatisfy(job -> assertThat(job.getServerSentEventSequence()).isNull());
    assertThat(jobMapper.findBySseSequenceRange(incidentId, 0L, Long.MAX_VALUE, 4)).isEmpty();

    // given: SSE 순번을 이미 확정한 작업이 있는 DB에 저장 순서 컬럼을 추가한다.
    long assignedSequence = incidentMapper.incrementAndGetSseSequence(incidentId);
    jobMapper.assignSseSequenceIfAbsent(jobIds.get(1), assignedSequence);

    // when: 저장 순서를 위한 실제 main migration을 이어서 적용한다.
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    // then: 기존 내용·확정 순번은 바뀌지 않고 알 수 없는 과거 저장 순서는 비워 둔다.
    assertThat(readJobContents(incidentId)).isEqualTo(jobsBefore);
    assertThat(readSourceContents(markerId, pathId)).isEqualTo(sourcesBefore);
    assertThat(incidentMapper.findLastSseSequence(incidentId)).isEqualTo(assignedSequence);
    assertThat(jobIds.stream().map(jobMapper::findById).toList())
        .extracting(ServerSentEventJob::getServerSentEventSequence)
        .containsExactly(null, assignedSequence, null, null);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT insertion_order FROM event_dispatch_job WHERE incident_id = ?",
                Long.class,
                incidentId))
        .hasSize(statuses.size())
        .containsOnlyNulls();
  }

  private void insertSourceData(UUID incidentId, UUID markerId, UUID pathId) {
    jdbcTemplate.update(
        """
        INSERT INTO marker (
            id, incident_id, operational_period_id, marker_type, location, memo,
            occurred_at, created_by_account_id, marker_source, status, version)
        VALUES (?, ?, gen_random_uuid(), 'PERSON_FOUND', ST_SetSRID(ST_Point(127, 37), 4326),
                'Original marker', now(), gen_random_uuid(), 'APP', 'ACTIVE', 1)
        """,
        markerId,
        incidentId);
    jdbcTemplate.update(
        """
        INSERT INTO marker_notification (
            id, marker_id, notification_type, recipient_rule, notification_payload, status, version)
        VALUES (gen_random_uuid(), ?, 'PERSON_FOUND', 'ALL_INCIDENT_ASSIGNED',
                '{"memo":"Original notification"}'::jsonb, 'SNAPSHOT_CREATED', 1)
        """,
        markerId);
    UUID accountId =
        jdbcTemplate.queryForObject("SELECT id FROM account ORDER BY id LIMIT 1", UUID.class);
    jdbcTemplate.update(
        """
        INSERT INTO search_path (id, duty_shift_id, account_id, status, started_at)
        VALUES (?, gen_random_uuid(), ?, 'RECORDING', now())
        """,
        pathId,
        accountId);
    jdbcTemplate.update(
        """
        INSERT INTO search_path_gps_point
            (search_path_id, point_order, point_id, client_ts, lon, lat, speed_mps)
        VALUES (?, 0, 'original-point', now(), 127, 37, 1)
        """,
        pathId);
  }

  private List<String> readJobContents(UUID incidentId) {
    return jdbcTemplate.queryForList(
        """
        SELECT (to_jsonb(job) - 'sse_sequence' - 'insertion_order')::text
        FROM event_dispatch_job job WHERE incident_id = ? ORDER BY id
        """,
        String.class,
        incidentId);
  }

  private List<String> readSourceContents(UUID markerId, UUID pathId) {
    return List.of(
        jdbcTemplate.queryForObject(
            "SELECT to_jsonb(m)::text FROM marker m WHERE id = ?", String.class, markerId),
        jdbcTemplate.queryForObject(
            "SELECT to_jsonb(n)::text FROM marker_notification n WHERE marker_id = ?",
            String.class,
            markerId),
        jdbcTemplate.queryForObject(
            "SELECT to_jsonb(p)::text FROM search_path p WHERE id = ?", String.class, pathId),
        jdbcTemplate.queryForObject(
            "SELECT to_jsonb(g)::text FROM search_path_gps_point g WHERE search_path_id = ?",
            String.class,
            pathId));
  }
}
