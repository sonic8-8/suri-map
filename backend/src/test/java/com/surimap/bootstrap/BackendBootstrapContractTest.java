package com.surimap.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendBootstrapContractTest {

  @Autowired private Flyway flyway;

  @Autowired private SqlSessionFactory sqlSessionFactory;

  @Autowired private BootstrapProbeMapper bootstrapProbeMapper;

  @Autowired private Environment environment;

  @Test
  void usesGradleGroovyDslBuildScripts() {
    var projectDir = Path.of("").toAbsolutePath();

    assertThat(projectDir.resolve("build.gradle")).exists();
    assertThat(projectDir.resolve("settings.gradle")).exists();
    assertThat(projectDir.resolve("build.gradle.kts")).doesNotExist();
    assertThat(projectDir.resolve("settings.gradle.kts")).doesNotExist();
  }

  @Test
  void startsBaseTestProfileWithH2FlywayAndMyBatisMapperScan() {
    assertThat(environment.getRequiredProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
    assertThat(flyway.info().current()).isNotNull();
    assertThat(sqlSessionFactory.getConfiguration().hasMapper(BootstrapProbeMapper.class)).isTrue();
    assertThat(bootstrapProbeMapper.migrationProbeCount()).isEqualTo(1);
  }
}
