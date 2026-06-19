package com.surimap.path;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("SearchPath query performance evidence")
@Tag("performance")
class SearchPathQueryPerformanceEvidenceTest extends PostGisIntegrationTestSupport {

  private static final UUID TARGET_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000099001");
  private static final UUID OTHER_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000099002");
  private static final UUID TARGET_OP_ID =
      UUID.fromString("65000000-0000-4000-8000-000000099001");
  private static final UUID OTHER_OP_ID =
      UUID.fromString("65000000-0000-4000-8000-000000099002");
  private static final UUID TARGET_ACCOUNT_ID =
      UUID.fromString("62000000-0000-4000-8000-000000099001");
  private static final UUID OTHER_ACCOUNT_ID =
      UUID.fromString("62000000-0000-4000-8000-000000099002");
  private static final UUID TARGET_PHONE_ID =
      UUID.fromString("50000000-0000-4000-8000-000000099001");
  private static final UUID OTHER_PHONE_ID =
      UUID.fromString("50000000-0000-4000-8000-000000099002");
  private static final UUID TARGET_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-4000-8000-000000099001");
  private static final UUID OTHER_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-4000-8000-000000099002");
  private static final UUID TARGET_DUTY_SHIFT_ID =
      UUID.fromString("60000000-0000-4000-8000-000000099001");
  private static final UUID OTHER_DUTY_SHIFT_ID =
      UUID.fromString("60000000-0000-4000-8000-000000099002");
  private static final Instant STARTED_AT = Instant.parse("2026-05-18T00:00:00Z");
  private static final int TARGET_PATH_COUNT = 25;
  private static final int UNRELATED_PATH_COUNT = 600;
  private static final int POINTS_PER_PATH = 24;

  @Autowired private SearchPathService searchPathService;

  @BeforeEach
  void cleanAndSeedPerformanceFixture() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
            event_dispatch_job,
            search_area_boundary_alert,
            search_path_lifecycle_event,
            search_path_excluded_point,
            search_path_segment,
            search_path,
            duty_shift,
            operational_period,
            incident_assignment,
            police_phone,
            account,
            incident
        CASCADE
        """);
    seedAccountsAndIncidents();
    seedPaths(TARGET_DUTY_SHIFT_ID, TARGET_ACCOUNT_ID, TARGET_PATH_COUNT, "target");
    seedPaths(OTHER_DUTY_SHIFT_ID, OTHER_ACCOUNT_ID, UNRELATED_PATH_COUNT, "unrelated");
  }

  @Test
  @DisplayName("measure filtered SearchPath query with many unrelated paths")
  void measureFilteredQueryWithManyUnrelatedPaths() {
    warmUp();

    List<Long> elapsedMillis = new ArrayList<>();
    PathQueryResponse response = null;
    for (int index = 0; index < 5; index++) {
      long started = System.nanoTime();
      response = searchPathService.query(TARGET_INCIDENT_ID, TARGET_OP_ID, null);
      elapsedMillis.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    assertThat(response).isNotNull();
    assertThat(response.paths()).hasSize(TARGET_PATH_COUNT);
    assertThat(totalPathRows()).isEqualTo(TARGET_PATH_COUNT + UNRELATED_PATH_COUNT);

    Collections.sort(elapsedMillis);
    System.out.println("PATH_QUERY_PERF totalPathRows=" + totalPathRows());
    System.out.println("PATH_QUERY_PERF targetPathRows=" + TARGET_PATH_COUNT);
    System.out.println("PATH_QUERY_PERF unrelatedPathRows=" + UNRELATED_PATH_COUNT);
    System.out.println("PATH_QUERY_PERF pointsPerPath=" + POINTS_PER_PATH);
    System.out.println("PATH_QUERY_PERF elapsedMillis=" + elapsedMillis);
    System.out.println("PATH_QUERY_PERF medianMillis=" + elapsedMillis.get(elapsedMillis.size() / 2));
    System.out.println("PATH_QUERY_PERF findAllPlan");
    explainFindAll().forEach(line -> System.out.println("PATH_QUERY_PLAN findAll " + line));
    System.out.println("PATH_QUERY_PERF filteredPlan");
    explainFiltered().forEach(line -> System.out.println("PATH_QUERY_PLAN filtered " + line));
  }

  private void warmUp() {
    searchPathService.query(TARGET_INCIDENT_ID, TARGET_OP_ID, null);
    searchPathService.query(TARGET_INCIDENT_ID, TARGET_OP_ID, null);
  }

  private void seedAccountsAndIncidents() {
    Timestamp startedAt = Timestamp.from(STARTED_AT);
    jdbcTemplate.update(
        """
        INSERT INTO account (
            id, login_id, password_hash, display_name, account_type, organization_type, status,
            created_at, updated_at
        )
        VALUES
            (?::uuid, 'acct-path-perf-target', '{noop}fixture', 'Path perf target',
             'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE', ?, ?),
            (?::uuid, 'acct-path-perf-other', '{noop}fixture', 'Path perf other',
             'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE', ?, ?)
        """,
        TARGET_ACCOUNT_ID.toString(),
        startedAt,
        startedAt,
        OTHER_ACCOUNT_ID.toString(),
        startedAt,
        startedAt);
    jdbcTemplate.update(
        """
        INSERT INTO police_phone (
            id, phone_code, display_name, account_id, status, registered,
            last_heartbeat_at, last_sync_at, version, created_at, updated_at
        )
        VALUES
            (?::uuid, 'dev-path-perf-target', 'Path perf target phone',
             ?::uuid, 'ACTIVE', TRUE, ?, ?, 1, ?, ?),
            (?::uuid, 'dev-path-perf-other', 'Path perf other phone',
             ?::uuid, 'ACTIVE', TRUE, ?, ?, 1, ?, ?)
        """,
        TARGET_PHONE_ID.toString(),
        TARGET_ACCOUNT_ID.toString(),
        startedAt,
        startedAt,
        startedAt,
        startedAt,
        OTHER_PHONE_ID.toString(),
        OTHER_ACCOUNT_ID.toString(),
        startedAt,
        startedAt,
        startedAt,
        startedAt);
    jdbcTemplate.update(
        """
        INSERT INTO incident (
            id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES
            (?::uuid, ?::uuid, 'Path performance target incident', 'OPEN', ?, 1, ?, ?),
            (?::uuid, ?::uuid, 'Path performance unrelated incident', 'OPEN', ?, 1, ?, ?)
        """,
        TARGET_INCIDENT_ID.toString(),
        UUID.fromString("64000000-0000-4000-8000-000000099001").toString(),
        startedAt,
        startedAt,
        startedAt,
        OTHER_INCIDENT_ID.toString(),
        UUID.fromString("64000000-0000-4000-8000-000000099002").toString(),
        startedAt,
        startedAt,
        startedAt);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
            id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at
        )
        VALUES
            (?::uuid, ?::uuid, ?::uuid, 'MEMBER', ?, ?, ?),
            (?::uuid, ?::uuid, ?::uuid, 'MEMBER', ?, ?, ?)
        """,
        TARGET_ASSIGNMENT_ID.toString(),
        TARGET_INCIDENT_ID.toString(),
        TARGET_ACCOUNT_ID.toString(),
        startedAt,
        startedAt,
        startedAt,
        OTHER_ASSIGNMENT_ID.toString(),
        OTHER_INCIDENT_ID.toString(),
        OTHER_ACCOUNT_ID.toString(),
        startedAt,
        startedAt,
        startedAt);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
            id, incident_id, sequence_number, status, reason, started_by_account_id,
            started_at, version, created_at, updated_at
        )
        VALUES
            (?::uuid, ?::uuid, 1, 'ACTIVE', 'INITIAL_IMPORT', ?::uuid, ?, 1, ?, ?),
            (?::uuid, ?::uuid, 1, 'ACTIVE', 'INITIAL_IMPORT', ?::uuid, ?, 1, ?, ?)
        """,
        TARGET_OP_ID.toString(),
        TARGET_INCIDENT_ID.toString(),
        TARGET_ACCOUNT_ID.toString(),
        startedAt,
        startedAt,
        startedAt,
        OTHER_OP_ID.toString(),
        OTHER_INCIDENT_ID.toString(),
        OTHER_ACCOUNT_ID.toString(),
        startedAt,
        startedAt,
        startedAt);
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
            id, operational_period_id, incident_assignment_id, police_phone_id, status,
            started_by_account_id, started_at, version, created_at, updated_at
        )
        VALUES
            (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'ACTIVE', ?::uuid, ?, 1, ?, ?),
            (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'ACTIVE', ?::uuid, ?, 1, ?, ?)
        """,
        TARGET_DUTY_SHIFT_ID.toString(),
        TARGET_OP_ID.toString(),
        TARGET_ASSIGNMENT_ID.toString(),
        TARGET_PHONE_ID.toString(),
        TARGET_ACCOUNT_ID.toString(),
        startedAt,
        startedAt,
        startedAt,
        OTHER_DUTY_SHIFT_ID.toString(),
        OTHER_OP_ID.toString(),
        OTHER_ASSIGNMENT_ID.toString(),
        OTHER_PHONE_ID.toString(),
        OTHER_ACCOUNT_ID.toString(),
        startedAt,
        startedAt,
        startedAt);
  }

  private void seedPaths(UUID dutyShiftId, UUID accountId, int count, String label) {
    jdbcTemplate.update(
        """
        INSERT INTO search_path (
            id,
            duty_shift_id,
            account_id,
            status,
            started_at,
            ended_at,
            geometry,
            version,
            created_at,
            updated_at
        )
        SELECT
            gen_random_uuid(),
            ?::uuid,
            ?::uuid,
            'RECORDING',
            ?::timestamptz + (path_index || ' seconds')::interval,
            NULL,
            (
                SELECT ST_SetSRID(
                    ST_MakeLine(
                        ST_MakePoint(
                            126.900000 + path_index * 0.00001 + point_index * 0.000001,
                            35.160000 + point_index * 0.000001
                        )
                        ORDER BY point_index
                    ),
                    4326
                )
                FROM generate_series(1, ?) AS point_index
            ),
            1,
            ?::timestamptz + (path_index || ' seconds')::interval,
            ?::timestamptz + (path_index || ' seconds')::interval
        FROM generate_series(1, ?) AS path_index
        """,
        dutyShiftId.toString(),
        accountId.toString(),
        STARTED_AT.toString(),
        POINTS_PER_PATH,
        STARTED_AT.toString(),
        STARTED_AT.toString(),
        count);
    System.out.println("PATH_QUERY_PERF seeded " + label + " paths=" + count);
  }

  private int totalPathRows() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM search_path", Integer.class);
  }

  private List<String> explainFindAll() {
    return jdbcTemplate.queryForList(
        """
        EXPLAIN (ANALYZE, BUFFERS)
        SELECT
            sp.id,
            op.incident_id,
            ds.operational_period_id AS op_id,
            sp.duty_shift_id,
            ds.police_phone_id,
            sp.account_id,
            sp.status,
            sp.started_at,
            sp.ended_at,
            ST_AsEWKT(sp.geometry) AS geometry,
            sp.version
        FROM search_path sp
        JOIN duty_shift ds
          ON ds.id = sp.duty_shift_id
        JOIN operational_period op
          ON op.id = ds.operational_period_id
        ORDER BY sp.updated_at DESC, sp.id ASC
        """,
        String.class);
  }

  private List<String> explainFiltered() {
    return jdbcTemplate.queryForList(
        """
        EXPLAIN (ANALYZE, BUFFERS)
        SELECT
            sp.id,
            op.incident_id,
            ds.operational_period_id AS op_id,
            sp.duty_shift_id,
            ds.police_phone_id,
            sp.account_id,
            sp.status,
            sp.started_at,
            sp.ended_at,
            ST_AsEWKT(sp.geometry) AS geometry,
            sp.version
        FROM search_path sp
        JOIN duty_shift ds
          ON ds.id = sp.duty_shift_id
        JOIN operational_period op
          ON op.id = ds.operational_period_id
        WHERE op.incident_id = ?::uuid
          AND ds.operational_period_id = ?::uuid
        ORDER BY sp.updated_at DESC, sp.id ASC
        """,
        String.class,
        TARGET_INCIDENT_ID.toString(),
        TARGET_OP_ID.toString());
  }
}
