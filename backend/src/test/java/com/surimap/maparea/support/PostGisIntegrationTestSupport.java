package com.surimap.maparea.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PostGIS 기반 Spring Boot 통합 테스트 공통 설정.
 * PostGIS extension, mapper SQL, TypeHandler 작동 검증 테스트용
 */
// TODO 통합테스트를 위한 인프라 기반이 존재하지 않을 것으로 예상하여, 우선 mr 기본 테스트 범위에서 제외.
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostGisIntegrationTestSupport {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.5")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("surimap")
            .withUsername("surimap")
            .withPassword("surimap");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGIS::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGIS::getUsername);
        registry.add("spring.datasource.password", POSTGIS::getPassword);
    }

    @BeforeEach
    void cleanS2Tables() {
        jdbcTemplate.execute("TRUNCATE TABLE search_area_history, search_area, map_boundary");
    }
}
