package com.surimap.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresContainerTestSupport {

    // 컨테이너를 static으로 공유해 테스트 클래스마다 재기동하지 않는다
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgis/postgis:16-3.5")
                    .withDatabaseName("surimap_test")
                    .withUsername("surimap")
                    .withPassword("surimap");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
