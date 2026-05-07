package com.surimap.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.domain.bootstrap.BootstrapMapper;
import java.nio.file.Path;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendBootstrapContractTest {

  @Autowired private Flyway flyway;

  @Autowired private SqlSessionFactory sqlSessionFactory;

  @Autowired private BootstrapMapper bootstrapMapper;

  @Test
  void usesGradleGroovyDslBuildScripts() {
    var projectDir = Path.of("").toAbsolutePath();

    assertThat(projectDir.resolve("build.gradle")).exists();
    assertThat(projectDir.resolve("settings.gradle")).exists();
    assertThat(projectDir.resolve("build.gradle.kts")).doesNotExist();
    assertThat(projectDir.resolve("settings.gradle.kts")).doesNotExist();
  }

  @Test
  void startsTestProfileWithFlywayAndMyBatisMapperScan() {
    assertThat(flyway.info().current()).isNotNull();
    assertThat(sqlSessionFactory.getConfiguration().hasMapper(BootstrapMapper.class)).isTrue();
    assertThat(bootstrapMapper.migrationProbeCount()).isEqualTo(1);
  }
}
