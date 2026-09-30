package com.surimap.global.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.surimap.account.security.OidcIdentityAuthenticationConverter;
import com.surimap.api.controller.sse.ServerSentEventController;
import com.surimap.api.service.sse.ServerSentEventHistoryService;
import com.surimap.api.service.sse.ServerSentEventSubscriptionService;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.config.SecurityConfig;
import com.surimap.config.ServerSentEventConfig;
import com.surimap.global.event.EventPublishRequest;
import jakarta.servlet.DispatcherType;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** 실제 Controller·Security filter·MVC·Tomcat·TCP 검사. 토큰 해석·사건 권한·DB 조회는 제외한다. */
class ServerSentEventStreamTest {

  @ParameterizedTest(name = "{1}")
  @CsvSource({
    "live, 실시간 전송: 수신을 재개하면 대기 중인 이벤트를 순서대로 받는다",
    "replay, 이력 재전송: 수신을 재개하면 다음 페이지와 실시간 이벤트를 순서대로 받는다",
    "terminal-replay, 재전송 중 사건 종료: 남은 이력 대신 종료 알림을 받고 응답을 끝낸다"
  })
  @DisplayName("느린 연결이 작업 스레드 수만큼 있어도 정상 SSE·HTTP는 응답한다")
  void slow_connections_do_not_block_healthy_requests_and_resume_in_order(
      String source, String expectedBehavior) throws Exception {
    // given: 작업 스레드 4개와 읽기를 멈출 연결 4개를 준비한다. 운영 권장값이 아니다.
    var context = new AnnotationConfigWebApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test", Map.of("surimap.cors.allowed-origin-patterns", "http://localhost")));
    context.register(HttpServer.class);
    var factory = new TomcatServletWebServerFactory(0);
    factory.setAddress(InetAddress.getByName("127.0.0.1"));
    factory.addConnectorCustomizers(
        connector -> {
          connector.setProperty("maxThreads", "4");
          connector.setProperty("minSpareThreads", "4");
          connector.setProperty("socket.txBufSize", "1024");
        });
    var server =
        factory.getWebServer(
            servletContext -> {
              context.setServletContext(servletContext);
              var security =
                  servletContext.addFilter(
                      "springSecurityFilterChain",
                      new DelegatingFilterProxy("springSecurityFilterChain", context));
              security.setAsyncSupported(true);
              security.addMappingForUrlPatterns(
                  EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC), false, "/*");
              var servlet = servletContext.addServlet("dispatcher", new DispatcherServlet(context));
              servlet.setLoadOnStartup(1);
              servlet.setAsyncSupported(true);
              servlet.addMapping("/");
            });
    var sockets = new ArrayList<Socket>();
    var readers = new ArrayList<BufferedReader>();
    var executor = Executors.newSingleThreadExecutor();
    UUID incidentId = UUID.randomUUID();
    HttpURLConnection healthy = null;
    try {
      server.start();
      var request = largeEvent(incidentId);
      var replay = context.getBean(ServerSentEventHistoryService.class);
      if (!source.equals("live")) {
        var firstPage = new ArrayList<ServerSentEventMessage>();
        firstPage.add(
            ServerSentEventMessage.builder()
                .id("1")
                .event(request.getType())
                .data(request)
                .build());
        if (source.equals("terminal-replay")) {
          firstPage.add(
              ServerSentEventMessage.builder()
                  .id("2")
                  .event(request.getType())
                  .data(request)
                  .build());
        }
        when(replay.getFirstPageAfter(any(), any()))
            .thenReturn(
                ServerSentEventHistoryService.HistoryPage.builder()
                    .messages(firstPage)
                    .throughSequence(2)
                    .build());
        when(replay.getNextPage(any(), any()))
            .thenReturn(
                ServerSentEventHistoryService.HistoryPage.builder()
                    .messages(
                        List.of(
                            ServerSentEventMessage.builder()
                                .id("2")
                                .event(request.getType())
                                .data(request)
                                .build()))
                    .throughSequence(2)
                    .build());
      }
      String path = "/api/incidents/" + incidentId + "/events";
      for (int index = 0; index < 4; index++) {
        var socket = new Socket();
        sockets.add(socket);
        socket.setReceiveBufferSize(1024);
        socket.setSoTimeout(3000);
        socket.setSoLinger(true, 0);
        socket.connect(new InetSocketAddress("127.0.0.1", server.getPort()), 3000);
        socket
            .getOutputStream()
            .write(
                ("GET "
                        + path
                        + " HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer test.header.signature\r\nX-Client-Channel: WEB\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
        var input = socket.getInputStream();
        assertThat(readLine(input)).contains("200");
        while (!readLine(input).isEmpty()) {}
        var reader =
            new BufferedReader(
                new InputStreamReader(new ChunkedBody(input), StandardCharsets.UTF_8));
        assertThat(reader.readLine()).isEqualTo(":connected");
        assertThat(reader.readLine()).isEmpty();
        readers.add(reader);
      }
      healthy = open(server.getPort(), path);
      var healthyReader =
          new BufferedReader(
              new InputStreamReader(healthy.getInputStream(), StandardCharsets.UTF_8));
      assertThat(healthyReader.readLine()).isEqualTo(":connected");
      assertThat(healthyReader.readLine()).isEmpty();
      var registry = context.getBean(ServerSentEventConnectionRegistry.class);
      var jobService = mock(ServerSentEventJobService.class);
      // when: 느린 소켓은 읽지 않고 정상 연결만 같은 이벤트를 읽는다.
      var received = executor.submit(() -> readFrame(healthyReader));
      if (source.equals("live")) {
        ServerSentEventTestSupport.dispatchLiveEvent(jobService, registry, request, 1);
      }

      // then: 느린 연결의 수신을 풀기 전에 정상 SSE와 일반 HTTP가 응답한다.
      assertThat(received.get(5, TimeUnit.SECONDS))
          .contains("id:1", "event:SEARCH_PATH_UPDATED", request.getEventId().toString());
      if (!source.equals("live")) {
        assertThat(readFrame(healthyReader)).startsWith("id:2\n");
        // 정상 연결만 다음 페이지를 읽는다. 소켓이 막힌 네 연결은 앞 페이지에 머문다.
        verify(replay, times(source.equals("replay") ? 1 : 0)).getNextPage(any(), any());
      }
      var health = open(server.getPort(), "/api/health");
      try {
        assertThat(new String(health.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
            .isEqualTo("ok");
      } finally {
        health.disconnect();
      }
      var terminal =
          EventPublishRequest.builder()
              .eventId(UUID.randomUUID())
              .incidentId(incidentId)
              .type("INCIDENT_CLOSED")
              .payloadFormatVersion(1)
              .sourceEntityType("incident")
              .sourceEntityId(incidentId)
              .occurredAt(request.getOccurredAt())
              .payload(Map.of("id", incidentId.toString(), "status", "CLOSED", "version", 1))
              .build();
      if (source.equals("terminal-replay")) {
        // 첫 프레임 쓰기가 멈춘 동안 종료되면, 같은 페이지의 미전송 2번은 버린다.
        ServerSentEventTestSupport.dispatchLiveEvent(jobService, registry, terminal, 3);
        sockets.get(0).setReceiveBufferSize(64 * 1024);
        assertThat(readFrame(readers.get(0))).startsWith("id:1\n");
        assertThat(readFrame(readers.get(0)))
            .startsWith("id:3\n")
            .contains("event:INCIDENT_CLOSED");
        assertThat(readers.get(0).readLine()).isNull();
        return;
      }
      sockets.get(0).setReceiveBufferSize(64 * 1024);
      String resumed = readFrame(readers.get(0));
      assertThat(resumed).contains("id:1", "x".repeat(256 * 1024));
      if (source.equals("live")) {
        ServerSentEventTestSupport.dispatchLiveEvent(jobService, registry, request, 2);
      }
      assertThat(readFrame(readers.get(0))).startsWith("id:2\n");
      if (source.equals("replay")) {
        verify(replay, times(2)).getNextPage(any(), any());
        ServerSentEventTestSupport.dispatchLiveEvent(jobService, registry, request, 3);
        assertThat(readFrame(readers.get(0))).startsWith("id:3\n");
      }
      // 종료 알림 뒤의 MVC 비동기 dispatch도 인증을 유지하며 JSON 오류 없이 응답을 마친다.
      ServerSentEventTestSupport.dispatchLiveEvent(
          jobService, registry, terminal, source.equals("replay") ? 4 : 3);
      assertThat(readFrame(readers.get(0))).contains("event:INCIDENT_CLOSED");
      assertThat(readers.get(0).readLine()).isNull();
    } finally {
      for (Socket socket : sockets) {
        socket.close();
      }
      if (healthy != null) {
        healthy.disconnect();
      }
      context.getBean(ServerSentEventConnectionRegistry.class).closeIncidentConnections(incidentId);
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
      server.stop();
      context.close();
    }
  }

  private static EventPublishRequest largeEvent(UUID incidentId) {
    return EventPublishRequest.builder()
        .eventId(UUID.randomUUID())
        .incidentId(incidentId)
        .type("SEARCH_PATH_UPDATED")
        .payloadFormatVersion(1)
        .sourceEntityType("search_path")
        .sourceEntityId(UUID.randomUUID())
        .occurredAt(Instant.parse("2026-09-29T00:00:00Z"))
        .payload(
            Map.of(
                "id",
                UUID.randomUUID().toString(),
                "status",
                "RECORDING",
                "version",
                1,
                "padding",
                "x".repeat(256 * 1024)))
        .build();
  }

  private static String readLine(InputStream input) throws IOException {
    var bytes = new ByteArrayOutputStream();
    int next;
    while ((next = input.read()) != '\n') {
      if (next < 0) {
        throw new IOException("Unexpected end of HTTP response");
      }
      if (next != '\r') {
        bytes.write(next);
      }
    }
    return bytes.toString(StandardCharsets.US_ASCII);
  }

  private static HttpURLConnection open(int port, String path) throws IOException {
    var connection =
        (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
    connection.setConnectTimeout(3000);
    connection.setReadTimeout(3000);
    connection.setRequestProperty("Authorization", "Bearer test.header.signature");
    connection.setRequestProperty("X-Client-Channel", "WEB");
    return connection;
  }

  private static String readFrame(BufferedReader reader) throws IOException {
    var frame = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null && !line.isEmpty()) {
      frame.append(line).append('\n');
    }
    return frame.toString();
  }

  private static final class ChunkedBody extends InputStream {
    private final InputStream input;
    private int remaining;
    private boolean endOfChunk;
    private boolean ended;

    private ChunkedBody(InputStream input) {
      this.input = input;
    }

    @Override
    public int read() throws IOException {
      if (ended) {
        return -1;
      }
      if (remaining == 0) {
        if (endOfChunk && !readLine(input).isEmpty()) {
          throw new IOException("Invalid HTTP chunk ending");
        }
        remaining = Integer.parseInt(readLine(input).split(";", 2)[0], 16);
        endOfChunk = true;
        if (remaining == 0) {
          ended = true;
          return -1;
        }
      }
      int value = input.read();
      if (value < 0) {
        throw new IOException("Truncated HTTP chunk");
      }
      remaining--;
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      if (length == 0) {
        return 0;
      }
      int first = read();
      if (first < 0) {
        return -1;
      }
      bytes[offset] = (byte) first;
      int count = Math.min(length - 1, remaining);
      if (count == 0) {
        return 1;
      }
      int read = input.read(bytes, offset + 1, count);
      if (read < 0) {
        throw new IOException("Truncated HTTP chunk");
      }
      remaining -= read;
      return 1 + read;
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  @Import({
    SecurityConfig.class,
    ServerSentEventConfig.class,
    ServerSentEventController.class,
    HealthController.class
  })
  static class HttpServer {
    @Bean
    JwtDecoder jwtDecoder() {
      return token -> Jwt.withTokenValue(token).header("alg", "test").subject("test").build();
    }

    @Bean
    OidcIdentityAuthenticationConverter authenticationConverter() {
      return (jwt, channel, phone) ->
          Optional.of(
              new SuriMapAuthentication(
                  UUID.randomUUID().toString(),
                  AccountType.COMMAND,
                  OrganizationType.MISSING_TEAM,
                  Channel.WEB,
                  null,
                  List.of()));
    }

    @Bean
    ServerSentEventHistoryService replayService() {
      var replay = mock(ServerSentEventHistoryService.class);
      when(replay.getFirstPageAfter(any(), any()))
          .thenReturn(
              ServerSentEventHistoryService.HistoryPage.builder().messages(List.of()).build());
      return replay;
    }

    @Bean
    ServerSentEventSubscriptionService sseStreamService(
        ServerSentEventConnectionRegistry registry, ServerSentEventHistoryService replay) {
      return new ServerSentEventSubscriptionService(
          replay, mock(ServerSentEventJobService.class), registry);
    }
  }

  @RestController
  static class HealthController {
    @GetMapping("/api/health")
    String health() {
      return "ok";
    }
  }
}
