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
import org.testcontainers.utility.DockerImageName;

@DisplayName("core runtime Flyway schema migration")
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
                "police_phone",
                "refresh_token",
                "fcm_token",
                "search_area_assignment",
                "search_path",
                "search_path_segment"));

        assertColumnType(connection, "account", "id", "uuid");
        assertColumnType(connection, "account", "login_id", "varchar");
        assertColumnType(connection, "police_phone", "id", "uuid");
        assertColumnType(connection, "police_phone", "account_id", "uuid");
        assertColumnType(connection, "police_phone", "registered", "bool");
        assertColumnType(connection, "police_phone", "version", "int8");
        assertColumnType(connection, "refresh_token", "account_id", "uuid");
        assertColumnType(connection, "fcm_token", "police_phone_id", "uuid");
        assertColumnType(connection, "search_area_assignment", "search_area_id", "uuid");
        assertColumnType(connection, "search_path", "duty_shift_id", "uuid");
        assertColumnType(connection, "search_path_segment", "search_path_id", "uuid");

        assertGeometryColumn(connection, "search_path", "geometry", "LINESTRING", 4326);
        assertGeometryColumn(connection, "search_path_segment", "geometry", "LINESTRING", 4326);

        assertIndexExists(connection, "account", "ux_account_login_id");
        assertIndexExists(connection, "police_phone", "ux_police_phone_code");
        assertIndexExists(connection, "police_phone", "idx_police_phone_account_status");
        assertIndexExists(connection, "fcm_token", "ux_fcm_token_police_phone_instance_active");
        assertIndexExists(connection, "search_area_assignment", "ux_search_area_assignment_active");
        assertIndexExists(connection, "search_path", "idx_search_path_geom");
        assertIndexExists(connection, "search_path_segment", "idx_search_path_segment_geom");
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
        assertThat(result.getBoolean(1))
            .as("index %s on %s exists", indexName, tableName)
            .isTrue();
      }
    }
  }
}
