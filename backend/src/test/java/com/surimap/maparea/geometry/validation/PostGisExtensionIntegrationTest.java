package com.surimap.maparea.geometry.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** L3-B01 PostGIS extension 검증. */
@DisplayName("L3-B01 PostGIS extension integration")
@Tag("integration")
class PostGisExtensionIntegrationTest extends PostGisIntegrationTestSupport {

  @Test
  @DisplayName("PostGIS extension이 활성화되어 있다")
  void postgis_extension_활성화_검증() {
    String version = jdbcTemplate.queryForObject("SELECT postgis_version()", String.class);

    assertThat(version).isNotBlank();
  }
}
