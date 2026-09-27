package com.surimap.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.common.health.HealthController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = TomcatProxyTest.HttpServer.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"surimap.auth.keycloak.issuer-uri=", "server.address=127.0.0.1"})
class TomcatProxyTest {

  private static final String WEB_ORIGIN = "https://suri-map.sonic8-8.com";
  private final HttpClient client = HttpClient.newHttpClient();
  @LocalServerPort private int port;

  @Test
  @DisplayName("신뢰한 프록시가 원래 HTTPS 주소를 전달하면, 같은 출처의 요청을 허용한다")
  void forwarded_https_origin_is_treated_as_same_origin() throws Exception {
    // given: 외부 HTTPS 요청이 프록시를 거쳐 내부 HTTP로 들어온다.
    HttpRequest request = forwardedRequest("/api/health").header("Origin", WEB_ORIGIN).build();

    // when: MockMvc가 아닌 실제 Tomcat과 보안 필터를 통과한다.
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // then: 별도 CORS 허용 목록에 추가하지 않아도 같은 출처로 판단한다.
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PATCH", "DELETE"})
  @DisplayName("같은 HTTPS 출처의 쓰기 요청이면, CORS를 통과하되 인증 없는 접근은 거부한다")
  void forwarded_write_request_reaches_authentication_check(String method) throws Exception {
    // given: 같은 웹 출처에서 보낸 요청이지만 인증 토큰은 없다.
    HttpRequest request =
        forwardedRequest("/api/markers/55555555-5555-5555-5555-555555550001")
            .header("Origin", WEB_ORIGIN)
            .header("X-Client-Channel", "WEB")
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString("{}"))
            .build();

    // when: 실제 HTTP 쓰기 요청을 보낸다.
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // then: CORS 오류가 아닌 기존 인증 오류를 반환하며 쓰기를 허용하지 않는다.
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).isEqualTo("{\"error\":\"unauthorized\"}");
  }

  @Test
  @DisplayName("다른 출처가 Forwarded 헤더를 위조해도, CORS 요청을 거부한다")
  void unapproved_origin_cannot_override_native_headers_with_forwarded_header() throws Exception {
    // given: 신뢰한 프록시의 주소와 다른 출처이며 표준 Forwarded 헤더도 위조했다.
    HttpRequest request =
        forwardedRequest("/api/health")
            .header("Origin", "https://untrusted.invalid")
            .header("Forwarded", "proto=https;host=untrusted.invalid")
            .build();

    // when: 원래 웹 주소와 다른 출처로 요청한다.
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // then: Native 설정의 신뢰한 주소를 사용하고 외부 출처는 거부한다.
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).isEqualTo("Invalid CORS request");
  }

  @Test
  @DisplayName("HTTPS 프록시가 표준과 다른 포트를 전달하면, 포트까지 포함해 같은 출처로 판단한다")
  void forwarded_non_default_port_is_preserved() throws Exception {
    // given: 프록시의 외부 HTTPS 포트가 8443이다.
    HttpRequest request =
        forwardedRequest("/api/health")
            .setHeader("X-Forwarded-Port", "8443")
            .header("Origin", WEB_ORIGIN + ":8443")
            .build();

    // when: 프록시가 전달한 포트로 출처를 비교한다.
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // then: 내부 서버 포트와 혼동하지 않는다.
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }

  @Test
  @DisplayName("전달 헤더 없는 직접 HTTP 요청도, 기존 상태 확인 API를 호출할 수 있다")
  void direct_http_request_remains_accessible() throws Exception {
    // given: 프록시를 거치지 않은 로컬 HTTP 요청이다.
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/health")).build();

    // when: 전달 헤더 없이 상태 확인 API를 호출한다.
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // then: 기존 직접 HTTP 호출도 정상 응답한다.
    assertThat(response.statusCode()).isEqualTo(200);
  }

  private HttpRequest.Builder forwardedRequest(String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .header("X-Forwarded-Host", "suri-map.sonic8-8.com")
        .header("X-Forwarded-Proto", "https")
        .header("X-Forwarded-Port", "443");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
  @Import({SecurityConfig.class, HealthController.class})
  static class HttpServer {}
}
