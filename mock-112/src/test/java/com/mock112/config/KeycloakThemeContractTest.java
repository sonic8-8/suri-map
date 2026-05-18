package com.mock112.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeycloakThemeContractTest {

    private static final Path THEME_DIR = Path.of("../infra/docker/keycloak/themes/suri-map/login");

    @Test
    @DisplayName("mock-112 Keycloak 로그인은 지휘 상황판 문구를 재사용하지 않는다")
    void mock112ClientUsesOwnLoginCopy() throws IOException {
        String loginTemplate = Files.readString(THEME_DIR.resolve("login.ftl"));
        String koreanMessages = Files.readString(THEME_DIR.resolve("messages/messages_ko.properties"));
        String englishMessages = Files.readString(THEME_DIR.resolve("messages/messages_en.properties"));

        assertThat(loginTemplate)
                .contains("suri-map-mock112")
                .contains("loginAccountTitleMock112")
                .contains("suriLoginHelpMock112");
        assertThat(koreanMessages)
                .contains("loginAccountTitleMock112=112 원천 시스템 계정으로 로그인")
                .contains("suriLoginHelpMock112=등록된 112 원천 시스템 계정으로 사건 원천 시스템에 접속합니다.");
        assertThat(englishMessages)
                .contains("loginAccountTitleMock112=112 원천 시스템 계정으로 로그인")
                .contains("suriLoginHelpMock112=등록된 112 원천 시스템 계정으로 사건 원천 시스템에 접속합니다.");

        String mock112Lines = koreanMessages.lines()
                .filter(line -> line.startsWith("loginAccountTitleMock112=") || line.startsWith("suriLoginHelpMock112="))
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(mock112Lines)
                .doesNotContain("지휘 상황판")
                .doesNotContain("지휘 계정")
                .doesNotContain("현장 앱");
    }
}
