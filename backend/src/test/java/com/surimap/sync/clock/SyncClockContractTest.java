package com.surimap.sync.clock;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
})
@AutoConfigureMockMvc
class SyncClockContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/sync/clock 응답은 clientTs, serverTs, clockOffsetMs, clockSyncedAt, maxAllowedSkewMs를 포함해야 한다")
    void syncClockReturnsOffsetFields() throws Exception {
        mockMvc.perform(post("/api/sync/clock")
                        .header(AUTHORIZATION, basicAuth())
                        .header("X-Device-Id", SyncClockContractFixtures.DEVICE_ID)
                        .header(CONTENT_TYPE, "application/json")
                        .content(SyncClockContractFixtures.VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientTs").value(SyncClockContractFixtures.CLIENT_TS))
                .andExpect(jsonPath("$.serverTs").isNotEmpty())
                .andExpect(jsonPath("$.clockOffsetMs").isNumber())
                .andExpect(jsonPath("$.clockSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.maxAllowedSkewMs").value(SyncClockContractFixtures.MAX_ALLOWED_SKEW_MS));
    }

    @Test
    @DisplayName("POST /api/sync/clock 은 허용 skew를 넘으면 clock_skew_exceeded 를 반환해야 한다")
    void syncClockRejectsSkewBeyondThreshold() throws Exception {
        mockMvc.perform(post("/api/sync/clock")
                        .header(AUTHORIZATION, basicAuth())
                        .header("X-Device-Id", SyncClockContractFixtures.DEVICE_ID)
                        .header(CONTENT_TYPE, "application/json")
                        .content(SyncClockContractFixtures.SKEWED_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("clock_skew_exceeded"));
    }

    private String basicAuth() {
        var token = Base64.getEncoder()
                .encodeToString("dev:dev-password".getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
