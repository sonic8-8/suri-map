package com.surimap;

import com.surimap.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BootstrapSmokeTest extends PostgresContainerTestSupport {

    @Test
    @DisplayName("Spring context가 test profile로 정상 기동된다")
    void contextLoads() {
        // context 로드 자체가 datasource, Flyway migration, MyBatis mapper scan을 검증한다
    }
}
