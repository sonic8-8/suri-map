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
  void usesGradleKotlinDslBuildScripts() {
    var projectDir = Path.of("").toAbsolutePath();

    assertThat(projectDir.resolve("build.gradle.kts")).exists();
    assertThat(projectDir.resolve("settings.gradle.kts")).exists();
    assertThat(projectDir.resolve("build.gradle")).doesNotExist();
    assertThat(projectDir.resolve("settings.gradle")).doesNotExist();
  }

  @Test
  void startsTestProfileWithFlywayAndMyBatisMapperScan() {
    assertThat(flyway.info().current()).isNotNull();
    assertThat(sqlSessionFactory.getConfiguration().hasMapper(BootstrapMapper.class)).isTrue();
    assertThat(bootstrapMapper.migrationProbeCount()).isEqualTo(1);
  }
}
