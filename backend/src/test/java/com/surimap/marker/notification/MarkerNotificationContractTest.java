package com.surimap.marker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkerNotificationContractTest {

  @Test
  @DisplayName("알림 테이블 생성 SQL은 저장 컬럼과 마커별 중복 방지 제약을 포함한다")
  void markerNotificationMigration_definesStorageColumnsAndMarkerUniqueness() throws Exception {
    // given: 마커 알림 테이블을 생성하는 Flyway 마이그레이션 파일이다.
    Path migrationPath =
        Path.of("src/main/resources/db/migration/V15__create_marker_notification.sql");

    // when: 마이그레이션 SQL 내용을 읽는다.
    String migration = Files.readString(migrationPath);

    // then: 알림 저장 컬럼과 같은 마커의 중복 알림을 막는 제약이 선언되어 있다.
    assertThat(migration).contains("CREATE TABLE IF NOT EXISTS marker_notification");
    assertThat(migration).contains("marker_id UUID NOT NULL");
    assertThat(migration).contains("notification_payload JSONB NOT NULL");
    assertThat(migration).contains("status VARCHAR(32) NOT NULL");
    assertThat(migration).contains("version BIGINT NOT NULL");
    assertThat(migration).contains("ux_marker_notification_marker");
    assertThat(migration).contains("ON marker_notification (marker_id)");
    assertThat(migration).contains("SUPPORT_REQUEST_CREATED");
    assertThat(migration).contains("SNAPSHOT_CREATED");
  }
}
