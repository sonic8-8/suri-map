package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.guard.GuardExceptionHandler;
import com.surimap.config.GuardConfig;
import com.surimap.config.SecurityConfig;
import com.surimap.eventhub.stream.EventStreamConfig;
import com.surimap.eventhub.stream.EventStreamController;
import com.surimap.eventhub.stream.EventStreamExceptionHandler;
import com.surimap.eventhub.stream.SseConnectionRegistry;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseReplayEvent;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.eventhub.stream.SseStreamService;
import com.surimap.support.auth.GuardPortTestStubs;
import com.surimap.support.auth.WithMockAccount;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.async.WebAsyncUtils;

@WebMvcTest(EventStreamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  SecurityConfig.class,
  GuardConfig.class,
  GuardExceptionHandler.class,
  GuardPortTestStubs.class,
  EventStreamConfig.class,
  EventStreamExceptionHandler.class
})
class EventStreamControllerTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID EVENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000901");
  private static final UUID DISPATCH_JOB_ID =
      UUID.fromString("70000000-0000-4000-8000-000000000901");

  @Autowired private MockMvc mockMvc;
  @Autowired private SseReplayEventStore replayStore;
  @Autowired private SseConnectionRegistry connectionRegistry;
  @Autowired private SseStreamService streamService;
  @Autowired private WebApplicationContext applicationContext;

  @BeforeEach
  void reset_replay_store() {
    replayStore.clear();
  }

  @AfterEach
  void close_incident_connections() {
    connectionRegistry.closeIncidentConnections(INCIDENT_ID);
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("웹 계정이 사건 이벤트를 구독하면 SSE 응답을 시작한다")
  void web_session_opens_incident_sse_stream() throws Exception {
    // given: 사건에 접근할 수 있는 웹 계정이다.
    // when / then: 구독 요청에 비동기 SSE 응답을 시작한다.
    mockMvc
        .perform(
            get("/api/incidents/{incidentId}/events", INCIDENT_ID)
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());
  }

  @Test
  @WithMockAccount(channel = Channel.APP, policePhoneId = "00000000-0000-0000-0000-000000000101")
  @DisplayName("앱 계정이 웹 전용 SSE를 구독하면 요청을 거부한다")
  void app_session_is_rejected_as_sse_consumer() throws Exception {
    // given: 현장 앱 계정이다.
    // when / then: 웹 전용 구독을 허용하지 않는다.
    mockMvc
        .perform(
            get("/api/incidents/{incidentId}/events", INCIDENT_ID)
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("마지막 수신 순번 다음 이력이 없으면 스트림 대신 재조회 오류를 반환한다")
  void replay_gap_returns_refetch_required() throws Exception {
    // given: 44번 다음에 와야 할 45번 이력이 없다.
    replayStore.save(
        SseReplayEvent.active(
            UUID.fromString("80000000-0000-4000-8000-000000000046"),
            DISPATCH_JOB_ID,
            INCIDENT_ID,
            46L,
            EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
            EventStreamTestFixtures.CREATED_AT));

    // when / then: HTTP 응답을 시작하기 전에 재조회 필요를 알린다.
    mockMvc
        .perform(
            get("/api/incidents/{incidentId}/events", INCIDENT_ID)
                .header("Last-Event-ID", "44")
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("gone_refetch_required"));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("SSE 응답 준비 후 제출한 작업에서 연결을 등록하고 지난 이벤트를 전송한다")
  void replay_starts_in_container_task_after_response_is_prepared() throws Exception {
    // given: 다시 보낼 이벤트가 있으며 컨테이너 작업 실행을 잠시 보류한다.
    replayStore.save(
        SseReplayEvent.active(
            UUID.randomUUID(),
            DISPATCH_JOB_ID,
            INCIDENT_ID,
            1L,
            EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
            EventStreamTestFixtures.CREATED_AT));
    List<Runnable> submittedTasks = new ArrayList<>();

    // when: 실제 MVC 반환 처리가 끝나고 컨테이너에 시작 작업이 제출된다.
    var result =
        mockMvc
            .perform(context -> new DeferredStartRequest(context, submittedTasks))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();

    // then: 시작 전에는 전송·등록하지 않고, 작업 실행 후 같은 순번과 내용을 보낸다.
    assertThat(submittedTasks).hasSize(1);
    assertThat(result.getResponse().getContentAsString()).isEmpty();
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
    submittedTasks.get(0).run();
    assertThat(result.getResponse().getContentAsString())
        .contains(":connected", "id:1", "event:PATH_APPENDED", EVENT_ID.toString());
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).hasSize(1);
  }

  @ParameterizedTest(name = "종료 원인: {0}")
  @ValueSource(strings = {"completion", "timeout", "error"})
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("초기 전송 작업 전에 연결이 종료되면 뒤늦게 전송하거나 연결을 등록하지 않는다")
  void completion_before_start_task_prevents_transmission_and_registration(String reason)
      throws Exception {
    // given: 응답 준비는 끝났지만 시작 작업은 아직 실행하지 않았다.
    List<Runnable> submittedTasks = new ArrayList<>();
    var result =
        mockMvc.perform(context -> new DeferredStartRequest(context, submittedTasks)).andReturn();
    var context = (MockAsyncContext) result.getRequest().getAsyncContext();

    // when: 컨테이너가 연결 종료를 알린 뒤 대기 중이던 작업이 실행된다.
    for (var listener : context.getListeners()) {
      switch (reason) {
        case "completion" -> listener.onComplete(new AsyncEvent(context));
        case "timeout" -> listener.onTimeout(new AsyncEvent(context));
        case "error" -> listener.onError(new AsyncEvent(context, new IOException("closed")));
        default -> throw new IllegalArgumentException(reason);
      }
    }

    // then: 종료된 응답을 사용하지 않으며 연결 목록에도 남지 않는다.
    assertThatCode(submittedTasks.get(0)::run).doesNotThrowAnyException();
    assertThat(result.getResponse().getContentAsString()).isEmpty();
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("컨테이너가 시작 작업을 거부하면 응답을 오류로 끝내고 연결을 남기지 않는다")
  void rejected_start_task_completes_response_with_error() throws Exception {
    // given: 컨테이너가 초기 전송 작업을 받지 못한다.
    var failure = new RejectedExecutionException("container unavailable");

    // when: 사건 이벤트 구독 요청의 시작 작업이 거부된다.
    var result =
        mockMvc
            .perform(
                context ->
                    new DeferredStartRequest(
                        context,
                        task -> {
                          throw failure;
                        }))
            .andReturn();

    // then: 작업 없이 열린 응답을 남기지 않고 MVC 오류 처리로 넘긴다.
    assertThat(WebAsyncUtils.getAsyncManager(result.getRequest()).hasConcurrentResult()).isTrue();
    assertThat(result.getAsyncResult()).isSameAs(failure);
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
  }

  @ParameterizedTest(name = "구독 대상: {0}")
  @ValueSource(strings = {"incident", "account"})
  @WithMockAccount(
      accountId = "11111111-1111-1111-1111-111111110003",
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("첫 응답 쓰기가 실패하면 연결을 제거하고 HTTP 오류 처리는 컨테이너에 맡긴다")
  void initial_io_failure_unregisters_connection_without_completing_response(String subscription)
      throws Exception {
    // given: 실제 MVC 전송 경계인 Servlet 응답에서 I/O 오류를 발생시킨다.
    var failingMvc = mvcWithWriteFailure(new IOException("connection closed"));
    List<Runnable> submittedTasks = new ArrayList<>();
    String path = "/api/incidents/" + INCIDENT_ID + "/events";
    if (subscription.equals("account")) {
      path = "/api/incidents/events";
    }
    String requestPath = path;
    var result =
        failingMvc
            .perform(context -> new DeferredStartRequest(context, requestPath, submittedTasks::add))
            .andExpect(request().asyncStarted())
            .andReturn();

    // when: 응답 준비 뒤 첫 연결 주석을 쓰다가 실패한다.
    assertThatCode(submittedTasks.get(0)::run).doesNotThrowAnyException();

    // then: 정리된 연결에 다시 보내지 않고 MVC 완료 처리를 중복 호출하지 않는다.
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
    assertThat(WebAsyncUtils.getAsyncManager(result.getRequest()).hasConcurrentResult()).isFalse();
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("MVC가 응답을 준비하던 중 연결 오류가 나면 초기 전송 작업을 제출하지 않는다")
  void error_during_response_initialization_prevents_start_task_submission() throws Exception {
    // given: Servlet 비동기 연결이 시작되자마자 컨테이너가 오류를 알린다.
    List<Runnable> submittedTasks = new ArrayList<>();
    var failure = new IOException("connection closed before emitter initialization");

    // when: emitter 초기화에 앞서 실제 MVC 오류 콜백이 실행된다.
    var result =
        mockMvc
            .perform(
                context ->
                    new DeferredStartRequest(context, submittedTasks) {
                      @Override
                      public AsyncContext startAsync(
                          ServletRequest request, ServletResponse response) {
                        var asyncContext =
                            new MockAsyncContext(request, response) {
                              @Override
                              public void addListener(AsyncListener listener) {
                                super.addListener(listener);
                                try {
                                  listener.onError(new AsyncEvent(this, failure));
                                } catch (IOException exception) {
                                  throw new UncheckedIOException(exception);
                                }
                              }

                              @Override
                              public void start(Runnable task) {
                                submittedTasks.add(task);
                              }
                            };
                        setAsyncContext(asyncContext);
                        setAsyncStarted(true);
                        return asyncContext;
                      }
                    })
            .andReturn();

    // then: 비동기 처리 시작 콜백만 보고 성공으로 판단하지 않는다.
    assertThat(result.getAsyncResult()).isInstanceOf(Throwable.class);
    assertThat(submittedTasks).isEmpty();
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("전송 내부 오류는 단순 연결 종료로 무시하지 않고 응답을 오류로 완료한다")
  void initial_runtime_failure_unregisters_connection_and_completes_response_with_error()
      throws Exception {
    // given: 응답 전송 중 I/O 오류가 아닌 내부 오류가 발생한다.
    var failure = new IllegalArgumentException("response conversion failed");
    var failingMvc = mvcWithWriteFailure(failure);
    List<Runnable> submittedTasks = new ArrayList<>();
    var result =
        failingMvc
            .perform(context -> new DeferredStartRequest(context, submittedTasks))
            .andReturn();

    // when: 초기 전송을 실행한다.
    assertThatCode(submittedTasks.get(0)::run).doesNotThrowAnyException();

    // then: 열린 응답을 남기지 않고 내부 오류를 MVC에 전달한다.
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
    assertThat(result.getAsyncResult())
        .isInstanceOfSatisfying(Throwable.class, error -> assertThat(error).hasRootCause(failure));
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("시작 작업을 기다리는 동안 추가된 이벤트도 재전송한다")
  void replay_includes_event_added_while_start_task_is_waiting() throws Exception {
    // given: 이력이 없는 상태로 접속하고 시작 작업은 보류한다.
    List<Runnable> submittedTasks = new ArrayList<>();
    var result =
        mockMvc.perform(context -> new DeferredStartRequest(context, submittedTasks)).andReturn();

    // when: 작업을 기다리는 동안 이벤트가 추가된 뒤 전송을 시작한다.
    streamService.dispatchLive(
        DISPATCH_JOB_ID,
        EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
        1L);
    submittedTasks.get(0).run();

    // then: 접속 시점의 빈 이력만 사용하지 않고 추가된 이벤트를 보낸다.
    assertThat(result.getResponse().getContentAsString())
        .contains("id:1", "event:PATH_APPENDED", EVENT_ID.toString());
  }

  @ParameterizedTest(name = "마지막 수신 순번: {0}")
  @ValueSource(strings = {"0", "1"})
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("종료 사건에 재접속하면 남은 종료 이벤트까지만 보내고 실시간 연결 없이 끝낸다")
  void terminal_replay_completes_response_without_registering_live_connection(String lastEventId)
      throws Exception {
    // given: 사건의 마지막 이벤트는 1번 종료 알림이다.
    replayStore.save(
        SseReplayEvent.active(
            UUID.randomUUID(),
            DISPATCH_JOB_ID,
            INCIDENT_ID,
            1L,
            EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "INCIDENT_CLOSED"),
            EventStreamTestFixtures.CREATED_AT));
    List<Runnable> submittedTasks = new ArrayList<>();
    var result =
        mockMvc
            .perform(
                context -> {
                  var request = new DeferredStartRequest(context, submittedTasks);
                  request.addHeader("Last-Event-ID", lastEventId);
                  return request;
                })
            .andReturn();

    // when: 초기 전송 작업을 실행한다.
    submittedTasks.get(0).run();

    // then: 이미 받은 이벤트는 반복하지 않으며 HTTP 응답과 구독을 끝낸다.
    if (lastEventId.equals("0")) {
      assertThat(result.getResponse().getContentAsString())
          .contains("id:1", "event:INCIDENT_CLOSED");
    } else {
      assertThat(result.getResponse().getContentAsString()).isEmpty();
    }
    assertThat(WebAsyncUtils.getAsyncManager(result.getRequest()).hasConcurrentResult()).isTrue();
    assertThat(result.getAsyncResult()).isNull();
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
  }

  @Test
  @WithMockAccount(
      accountId = "11111111-1111-1111-1111-111111110003",
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("계정 구독도 응답 준비 뒤 시작해 이벤트를 받고 종료 후에는 전송하지 않는다")
  void account_stream_starts_after_response_preparation_and_stops_on_completion() throws Exception {
    // given: 계정 구독의 전송 시작을 보류한다.
    var accountId = UUID.fromString("11111111-1111-1111-1111-111111110003");
    List<Runnable> submittedTasks = new ArrayList<>();
    var result =
        mockMvc
            .perform(
                context ->
                    new DeferredStartRequest(context, "/api/incidents/events", submittedTasks::add))
            .andReturn();
    assertThat(result.getResponse().getContentAsString()).isEmpty();

    // when: 구독을 시작하고 계정 대상 이벤트를 보낸다.
    submittedTasks.get(0).run();
    var frame =
        new SseEventFrame(
            "1",
            "INCIDENT_CREATED",
            EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "INCIDENT_CREATED"));
    connectionRegistry.sendToAccount(accountId, frame);
    String received = result.getResponse().getContentAsString();
    assertThat(received).contains(":connected", "id:1", "event:INCIDENT_CREATED");
    var context = (MockAsyncContext) result.getRequest().getAsyncContext();
    for (var listener : context.getListeners()) {
      listener.onComplete(new AsyncEvent(context));
    }

    // then: 종료 뒤 다시 보내도 응답은 바뀌지 않는다.
    connectionRegistry.sendToAccount(accountId, frame);
    assertThat(result.getResponse().getContentAsString()).isEqualTo(received);
  }

  private MockMvc mvcWithWriteFailure(Exception failure) {
    return MockMvcBuilders.webAppContextSetup(applicationContext)
        .addFilter(
            (request, response, chain) -> {
              var failingResponse = spy((HttpServletResponse) response);
              doThrow(failure).when(failingResponse).getOutputStream();
              chain.doFilter(request, failingResponse);
            })
        .build();
  }

  private static class DeferredStartRequest extends MockHttpServletRequest {
    private final Consumer<Runnable> submitTask;

    DeferredStartRequest(ServletContext context, List<Runnable> submittedTasks) {
      this(context, submittedTasks::add);
    }

    DeferredStartRequest(ServletContext context, Consumer<Runnable> submitTask) {
      this(context, "/api/incidents/" + INCIDENT_ID + "/events", submitTask);
    }

    DeferredStartRequest(ServletContext context, String path, Consumer<Runnable> submitTask) {
      super(context, "GET", path);
      this.submitTask = submitTask;
      setAsyncSupported(true);
    }

    @Override
    public AsyncContext startAsync(ServletRequest request, ServletResponse response) {
      var context =
          new MockAsyncContext(request, response) {
            @Override
            public void start(Runnable task) {
              submitTask.accept(task);
            }
          };
      setAsyncContext(context);
      setAsyncStarted(true);
      return context;
    }
  }
}
