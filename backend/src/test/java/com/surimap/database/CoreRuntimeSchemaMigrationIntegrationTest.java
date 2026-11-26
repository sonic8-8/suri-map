package com.surimap.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DisplayName("core runtime Flyway schema migration")
@Testcontainers(disabledWithoutDocker = true)
class CoreRuntimeSchemaMigrationIntegrationTest {

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  @Test
  @DisplayName("main migrations create DB/API/spec core runtime tables on PostgreSQL/PostGIS")
  void mainMigrationsCreateCoreRuntimeTables() throws Exception {
    try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)) {
      postgres.start();

      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .load()
          .migrate();

      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        assertTablesExist(
            connection,
            List.of(
                "account",
                "fcm_token",
                "search_area_assignment",
                "search_path",
                "search_path_segment",
                "idempotency_record",
                "offline_package_manifest",
                "offline_package_installation",
                "event_dispatch_job",
                "marker_notification"));

        assertColumnType(connection, "incident", "source_incident_id", "uuid");
        assertColumnType(connection, "account", "id", "uuid");
        assertColumnType(connection, "account", "login_id", "varchar");
        assertColumnType(connection, "fcm_token", "police_phone_id", "uuid");
        assertColumnType(connection, "search_area_assignment", "search_area_id", "uuid");
        assertColumnType(connection, "search_path", "duty_shift_id", "uuid");
        assertColumnType(connection, "search_path_segment", "search_path_id", "uuid");
        assertColumnType(connection, "offline_package_manifest", "id", "uuid");
        assertColumnType(connection, "offline_package_manifest", "incident_id", "uuid");
        assertColumnType(connection, "offline_package_manifest", "operational_period_id", "uuid");
        assertColumnType(connection, "offline_package_manifest", "overall_search_area_id", "uuid");
        assertColumnType(connection, "offline_package_installation", "id", "uuid");
        assertColumnType(
            connection, "offline_package_installation", "offline_package_manifest_id", "uuid");
        assertColumnType(connection, "offline_package_installation", "police_phone_id", "uuid");
        assertColumnType(
            connection, "offline_package_installation", "last_reported_by_account_id", "uuid");
        assertColumnType(connection, "event_dispatch_job", "event_id", "uuid");
        assertColumnType(connection, "event_dispatch_job", "source_entity_id", "uuid");
        assertColumnType(connection, "marker_notification", "recipient_account_ids", "_uuid");
        assertColumnType(connection, "idempotency_record", "id", "uuid");
        assertColumnType(connection, "idempotency_record", "idempotency_key", "varchar");
        assertColumnType(connection, "idempotency_record", "request_body_hash", "bpchar");
        assertColumnType(connection, "idempotency_record", "response_body_json", "text");
        assertColumnType(connection, "idempotency_record", "response_content_type", "varchar");
        assertColumnType(connection, "idempotency_record", "response_body_format_version", "int4");
        assertColumnType(connection, "idempotency_record", "result_entity_id", "uuid");
        assertColumnType(connection, "idempotency_record", "result_entity_version", "int8");
        assertColumnType(connection, "idempotency_record", "result_entity_sequence", "int8");
        assertColumnType(connection, "idempotency_record", "replay_recovered", "bool");

        assertGeometryColumn(connection, "search_path", "geometry", "LINESTRING", 4326);
        assertGeometryColumn(connection, "search_path_segment", "geometry", "LINESTRING", 4326);

        assertIndexExists(connection, "account", "ux_account_login_id");
        assertIndexExists(connection, "fcm_token", "ux_fcm_token_police_phone_instance_active");
        assertIndexExists(connection, "search_area_assignment", "ux_search_area_assignment_active");
        assertIndexExists(connection, "search_path", "idx_search_path_geom");
        assertIndexExists(connection, "search_path_segment", "idx_search_path_segment_geom");
        assertIndexExists(
            connection, "idempotency_record", "ux_idempotency_record_key_path_method");
        assertTableDoesNotExist(connection, "refresh_token");
        assertTableDoesNotExist(connection, "police_phone");
      }
    }
  }

  @Test
  @DisplayName(
      "incident source_incident_id migration normalizes legacy smoke alias before UUID cast")
  void incidentSourceIncidentIdMigrationNormalizesLegacySmokeAlias() throws Exception {
    try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)) {
      postgres.start();

      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .target("20260513.006")
          .load()
          .migrate();

      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        try (var statement = connection.createStatement()) {
          statement.executeUpdate(
              """
              INSERT INTO incident (
                  id, source_incident_id, title, status, opened_at,
                  version, created_at, updated_at
              )
              VALUES (
                  '10000000-0000-4000-8000-000000000001',
                  'smoke-incident-001',
                  'Smoke incident',
                  'OPEN',
                  CURRENT_TIMESTAMP,
                  1,
                  CURRENT_TIMESTAMP,
                  CURRENT_TIMESTAMP
              )
              """);
        }
      }

      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .load()
          .migrate();

      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        assertColumnType(connection, "incident", "source_incident_id", "uuid");
        try (var statement =
            connection.prepareStatement(
                """
                SELECT source_incident_id::text
                FROM incident
                WHERE id = '10000000-0000-4000-8000-000000000001'::uuid
                """)) {
          try (ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("00000000-0000-0000-0000-000000000001");
          }
        }
      }
    }
  }

  @Test
  @DisplayName("마커 업무폰 컬럼 삭제 migration은 저장된 마커 이벤트와 알림 snapshot을 새 payload 형식으로 바꾼다")
  void markerPolicePhoneMigration_storedMarkerPayloads_removesPolicePhoneKeysAndFillsAuthorName()
      throws Exception {
    try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)) {
      postgres.start();

      // given: 배포 전 형식으로 저장된 마커 이벤트, 마커 알림 이벤트, 배정 변경 이벤트, 알림 snapshot이 있다.
      // 발견 알림의 마커에는 작성 계정이 있다. 지원 요청 알림의 마커는 DB에 없다.
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .target("20261120.001")
          .load()
          .migrate();
      try (Connection connection =
              DriverManager.getConnection(
                  postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
          var statement = connection.createStatement()) {
        statement.executeUpdate(
            """
            INSERT INTO account (
                id, login_id, password_hash, display_name, account_type, organization_type,
                status, created_at, updated_at
            )
            VALUES (
                'a0000000-0000-4000-8000-000000005701', 'acct-5701-author', '{noop}fixture',
                '작성자 김현장', 'TEAM', 'MISSING_TEAM', 'ACTIVE', now(), now()
            )
            """);
        statement.executeUpdate(
            """
            INSERT INTO marker (
                id, incident_id, operational_period_id, marker_type, location, occurred_at,
                created_by_account_id, police_phone_id, marker_source, status, version
            )
            VALUES (
                'c0000000-0000-4000-8000-000000005701', gen_random_uuid(), gen_random_uuid(),
                'PERSON_FOUND', ST_SetSRID(ST_MakePoint(126.9134, 35.1631), 4326), now(),
                'a0000000-0000-4000-8000-000000005701', gen_random_uuid(), 'APP', 'ACTIVE', 1
            )
            """);
        statement.executeUpdate(
            """
            INSERT INTO event_dispatch_job (
                event_id, incident_id, event_type, payload, source_entity_type,
                source_entity_id, occurred_at
            )
            VALUES
              ('e0000000-0000-4000-8000-000000005701', gen_random_uuid(), 'MARKER_CREATED',
               '{"id":"m1","policePhoneId":"p1","status":"ACTIVE"}', 'marker',
               gen_random_uuid(), now()),
              ('e0000000-0000-4000-8000-000000005702', gen_random_uuid(), 'PERSON_FOUND',
               '{"id":"n1","markerId":"c0000000-0000-4000-8000-000000005701","policePhoneId":"p1","policePhoneName":"업무폰","recipientPolicePhoneIds":["p2"],"recipientAccountIds":["a1"]}',
               'marker_notification', gen_random_uuid(), now()),
              ('e0000000-0000-4000-8000-000000005703', gen_random_uuid(),
               'INCIDENT_ASSIGNMENT_CHANGED', '{"id":"i1","recipientPolicePhoneIds":["p2"]}',
               'incident', gen_random_uuid(), now()),
              ('e0000000-0000-4000-8000-000000005704', gen_random_uuid(),
               'SUPPORT_REQUEST_CREATED',
               '{"id":"n2","markerId":"c0000000-0000-4000-8000-000000005799","policePhoneName":"업무폰"}',
               'marker_notification', gen_random_uuid(), now())
            """);
        statement.executeUpdate(
            """
            INSERT INTO marker_notification (
                id, marker_id, notification_type, recipient_rule, recipient_account_ids,
                recipient_police_phone_ids, notification_payload, status, version
            )
            VALUES (
                'f0000000-0000-4000-8000-000000005701', 'c0000000-0000-4000-8000-000000005701',
                'PERSON_FOUND', 'ALL_INCIDENT_ASSIGNED', ARRAY[]::uuid[], ARRAY[]::uuid[],
                '{"id":"n1","markerId":"c0000000-0000-4000-8000-000000005701","policePhoneId":"p1","policePhoneName":"업무폰","recipientPolicePhoneIds":["p2"],"recipientAccountIds":["a1"]}',
                'SNAPSHOT_CREATED', 1
            )
            """);
      }

      // when: 남은 migration을 적용한다.
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .load()
          .migrate();

      // then: 마커와 마커 알림 행에는 업무폰 키가 없다.
      // then: 작성 계정을 찾은 발견 알림에는 작성 계정 이름이 채워진다.
      // then: 마커를 찾지 못한 지원 요청 알림에는 이름이 채워지지 않는다.
      // then: 배정 변경 이벤트는 바뀌지 않는다.
      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        assertThat(eventPayload(connection, "e0000000-0000-4000-8000-000000005701"))
            .isEqualTo("{\"id\": \"m1\", \"status\": \"ACTIVE\"}");
        assertThat(eventPayload(connection, "e0000000-0000-4000-8000-000000005702"))
            .isEqualTo(
                "{\"id\": \"n1\", \"markerId\": \"c0000000-0000-4000-8000-000000005701\","
                    + " \"recipientAccountIds\": [\"a1\"],"
                    + " \"createdByAccountDisplayName\": \"작성자 김현장\"}");
        assertThat(eventPayload(connection, "e0000000-0000-4000-8000-000000005703"))
            .isEqualTo("{\"id\": \"i1\", \"recipientPolicePhoneIds\": [\"p2\"]}");
        assertThat(eventPayload(connection, "e0000000-0000-4000-8000-000000005704"))
            .isEqualTo(
                "{\"id\": \"n2\", \"markerId\": \"c0000000-0000-4000-8000-000000005799\"}");
        try (var statement =
                connection.prepareStatement(
                    """
                    SELECT notification_payload::text
                    FROM marker_notification
                    WHERE id = 'f0000000-0000-4000-8000-000000005701'::uuid
                    """);
            ResultSet result = statement.executeQuery()) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1))
              .isEqualTo(
                  "{\"id\": \"n1\", \"markerId\": \"c0000000-0000-4000-8000-000000005701\","
                      + " \"recipientAccountIds\": [\"a1\"],"
                      + " \"createdByAccountDisplayName\": \"작성자 김현장\"}");
        }
      }
    }
  }

  @Test
  @DisplayName("근무의 업무폰 컬럼 삭제 migration은 업무폰 ID만 지우고 근무 행과 계정 연결은 남긴다")
  void dutyShiftPolicePhoneMigration_storedDutyShift_dropsPolicePhoneIdAndKeepsRow()
      throws Exception {
    try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)) {
      postgres.start();

      // given: 배포 전 형식으로 업무폰 ID가 들어간 근무가 있다.
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .target("20261125.001")
          .load()
          .migrate();
      try (Connection connection =
              DriverManager.getConnection(
                  postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
          var statement = connection.createStatement()) {
        statement.executeUpdate(
            """
            INSERT INTO duty_shift (
                id, operational_period_id, incident_assignment_id, police_phone_id, status,
                started_by_account_id, started_at, version, created_at, updated_at
            )
            VALUES (
                'd0000000-0000-4000-8000-000000005801', 'b0000000-0000-4000-8000-000000005801',
                'a1000000-0000-4000-8000-000000005801', gen_random_uuid(), 'ACTIVE',
                'a0000000-0000-4000-8000-000000005801', now(), 1, now(), now()
            )
            """);
      }

      // when: 남은 migration을 적용한다.
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .load()
          .migrate();

      // then: 업무폰 ID 컬럼과 인덱스는 없고, 근무 행은 배정과 시작 계정을 그대로 가진다.
      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        assertColumnDoesNotExist(connection, "duty_shift", "police_phone_id");
        assertIndexDoesNotExist(connection, "duty_shift", "idx_duty_shift_phone");
        try (var statement =
                connection.prepareStatement(
                    """
                    SELECT incident_assignment_id::text, started_by_account_id::text, status
                    FROM duty_shift
                    WHERE id = 'd0000000-0000-4000-8000-000000005801'::uuid
                    """);
            ResultSet result = statement.executeQuery()) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("a1000000-0000-4000-8000-000000005801");
          assertThat(result.getString(2)).isEqualTo("a0000000-0000-4000-8000-000000005801");
          assertThat(result.getString(3)).isEqualTo("ACTIVE");
        }
      }
    }
  }

  @Test
  @DisplayName("업무폰 테이블 삭제 migration을 적용하면 police_phone과 그 테이블을 참조하는 FK만 지워진다. 업무폰 ID 값은 남는다")
  void policePhoneMigration_policePhoneRowsAndReferencingFk_dropsTableAndKeepsPolicePhoneIds()
      throws Exception {
    try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)) {
      postgres.start();

      // given: 배포 전 형식의 DB다. 업무폰 행이 있다. FCM 토큰은 그 업무폰 ID를 FK로 참조한다.
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .target("20261125.002")
          .load()
          .migrate();
      try (Connection connection =
              DriverManager.getConnection(
                  postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
          var statement = connection.createStatement()) {
        statement.executeUpdate(
            """
            INSERT INTO police_phone (id, phone_code, display_name, account_id, status, registered)
            VALUES (
                'b0000000-0000-4000-8000-000000006101', 'phone-6101', '팀폰 6101',
                '11111111-1111-1111-1111-111111110003', 'ACTIVE', TRUE
            )
            """);
        statement.executeUpdate(
            """
            ALTER TABLE fcm_token
                ADD CONSTRAINT fk_fcm_token_police_phone_6101
                FOREIGN KEY (police_phone_id) REFERENCES police_phone(id)
            """);
        statement.executeUpdate(
            """
            INSERT INTO fcm_token (
                id, account_id, police_phone_id, app_instance_id, token_hash, token_ciphertext,
                status, version
            )
            VALUES (
                'f1000000-0000-4000-8000-000000006101', '11111111-1111-1111-1111-111111110003',
                'b0000000-0000-4000-8000-000000006101', 'app-6101', 'hash-6101',
                'cipher:token-6101', 'ACTIVE', 1
            )
            """);
      }

      // when: 남은 migration을 적용한다.
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .load()
          .migrate();

      // then: police_phone 테이블은 없다.
      // then: FCM 토큰 행은 업무폰 ID 값을 그대로 가진다.
      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        assertTableDoesNotExist(connection, "police_phone");
        try (var statement =
                connection.prepareStatement(
                    """
                    SELECT police_phone_id::text
                    FROM fcm_token
                    WHERE id = 'f1000000-0000-4000-8000-000000006101'::uuid
                    """);
            ResultSet result = statement.executeQuery()) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("b0000000-0000-4000-8000-000000006101");
        }
      }
    }
  }

  private static void assertColumnDoesNotExist(
      Connection connection, String tableName, String columnName) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
            )
            """)) {
      statement.setString(1, tableName);
      statement.setString(2, columnName);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1))
            .as("%s.%s does not exist", tableName, columnName)
            .isFalse();
      }
    }
  }

  private static void assertIndexDoesNotExist(
      Connection connection, String tableName, String indexName) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = ?
                  AND indexname = ?
            )
            """)) {
      statement.setString(1, tableName);
      statement.setString(2, indexName);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).as("index %s does not exist", indexName).isFalse();
      }
    }
  }

  private static String eventPayload(Connection connection, String eventId) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "SELECT payload::text FROM event_dispatch_job WHERE event_id = ?::uuid")) {
      statement.setString(1, eventId);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getString(1);
      }
    }
  }

  private static void assertTablesExist(Connection connection, List<String> tableNames)
      throws SQLException {
    for (String tableName : tableNames) {
      try (var statement =
          connection.prepareStatement(
              """
              SELECT EXISTS (
                  SELECT 1
                  FROM information_schema.tables
                  WHERE table_schema = 'public'
                    AND table_name = ?
              )
              """)) {
        statement.setString(1, tableName);
        try (ResultSet result = statement.executeQuery()) {
          assertThat(result.next()).isTrue();
          assertThat(result.getBoolean(1)).as("table %s exists", tableName).isTrue();
        }
      }
    }
  }

  private static void assertTableDoesNotExist(Connection connection, String tableName)
      throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
            )
            """)) {
      statement.setString(1, tableName);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).as("table %s does not exist", tableName).isFalse();
      }
    }
  }

  private static void assertColumnType(
      Connection connection, String tableName, String columnName, String expectedUdtName)
      throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            SELECT udt_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = ?
              AND column_name = ?
            """)) {
      statement.setString(1, tableName);
      statement.setString(2, columnName);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).as("%s.%s exists", tableName, columnName).isTrue();
        assertThat(result.getString(1)).isEqualTo(expectedUdtName);
      }
    }
  }

  private static void assertGeometryColumn(
      Connection connection,
      String tableName,
      String columnName,
      String expectedType,
      int expectedSrid)
      throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            SELECT type, srid
            FROM geometry_columns
            WHERE f_table_schema = 'public'
              AND f_table_name = ?
              AND f_geometry_column = ?
            """)) {
      statement.setString(1, tableName);
      statement.setString(2, columnName);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).as("%s.%s geometry exists", tableName, columnName).isTrue();
        assertThat(result.getString(1)).isEqualTo(expectedType);
        assertThat(result.getInt(2)).isEqualTo(expectedSrid);
      }
    }
  }

  private static void assertIndexExists(Connection connection, String tableName, String indexName)
      throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            SELECT EXISTS (
                SELECT 1
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = ?
                  AND indexname = ?
            )
            """)) {
      statement.setString(1, tableName);
      statement.setString(2, indexName);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean(1)).as("index %s on %s exists", indexName, tableName).isTrue();
      }
    }
  }
}
