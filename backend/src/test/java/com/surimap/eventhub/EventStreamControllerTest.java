package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
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
import com.surimap.eventhub.adapter.EventDispatchJobService;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.stream.EventStreamConfig;
import com.surimap.eventhub.stream.EventStreamController;
import com.surimap.eventhub.stream.EventStreamExceptionHandler;
import com.surimap.eventhub.stream.GoneRefetchRequiredException;
import com.surimap.eventhub.stream.InMemorySseReplayEventStore;
import com.surimap.eventhub.stream.SseConnectionRegistry;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseReplayEvent;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.eventhub.stream.SseReplayService;
import com.surimap.eventhub.stream.SseReplayService.ReplayResult;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
  SseStreamService.class,
  EventStreamExceptionHandler.class
})
class EventStreamControllerTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID EVENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000901");
  private static final UUID DISPATCH_JOB_ID =
      UUID.fromString("70000000-0000-4000-8000-000000000901");

  @Autowired private MockMvc mockMvc;
  // HTTP 전송·연결 전환용 입력이다. DB 조회 자체는 SseReplayServiceTest에서 검증한다.
  private final SseReplayEventStore replayStore = new InMemorySseReplayEventStore();
  @MockitoBean private SseReplayService replayService;
  @MockitoBean private EventDispatchJobService jobService;
  @Autowired private SseConnectionRegistry connectionRegistry;
  @Autowired private SseStreamService streamService;
  @Autowired private WebApplicationContext applicationContext;

  @BeforeEach
  void reset_replay_store() {
    replayStore.clear();
    when(replayService.replayResultAfter(any(), nullable(String.class)))
        .thenAnswer(
            invocation -> {
              String lastId = invocation.getArgument(1);
              long cursor = lastId == null || lastId.isBlank() ? 0 : Long.parseLong(lastId);
              long through =
                  replayStore.findByIncidentId(INCIDENT_ID).stream()
                      .mapToLong(SseReplayEvent::replaySequence)
                      .max()
                      .orElse(0L);
              var remaining = replayStore.replayAfter(INCIDENT_ID, cursor);
              if (cursor > 0
                  && !remaining.isEmpty()
                  && remaining.get(0).replaySequence() > cursor + 1) {
                throw new GoneRefetchRequiredException();
              }
              return replay_page(cursor, through);
            });
    when(replayService.replayNextPage(any(), any()))
        .thenAnswer(
            invocation -> {
              ReplayResult previous = invocation.getArgument(1);
              long cursor =
                  Long.parseLong(previous.getFrames().get(previous.getFrames().size() - 1).id());
              return replay_page(cursor, previous.getThroughSequence());
            });
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
    dispatch_event(
        DISPATCH_JOB_ID,
        EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
        1L);
    submittedTasks.get(0).run();

    // then: 접속 시점의 빈 이력만 사용하지 않고 추가된 이벤트를 보낸다.
    assertThat(result.getResponse().getContentAsString())
        .contains("id:1", "event:PATH_APPENDED", EVENT_ID.toString());
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("과거 이벤트를 보내는 중 새 이벤트가 도착해도 과거 순번 뒤에 이어서 보낸다")
  void new_event_during_replay_is_sent_after_historical_event() throws Exception {
    // given: 1번 이력이 있으며 첫 응답을 쓸 때 2번 이벤트가 도착한다.
    replayStore.save(
        SseReplayEvent.active(
            UUID.randomUUID(),
            DISPATCH_JOB_ID,
            INCIDENT_ID,
            1L,
            EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
            EventStreamTestFixtures.CREATED_AT));
    var interleavingMvc =
        interceptFirstResponseWrite(
            () -> {
              dispatch_event(
                  DISPATCH_JOB_ID,
                  EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
                  1L);
              dispatch_event(
                  UUID.randomUUID(),
                  EventStreamTestFixtures.publishRequest(
                      UUID.randomUUID(), INCIDENT_ID, "PATH_APPENDED"),
                  2L);
            });
    List<Runnable> submittedTasks = new ArrayList<>();

    // when: MVC가 준비한 응답에서 과거 이력 전송을 시작한다.
    var result =
        interleavingMvc
            .perform(context -> new DeferredStartRequest(context, submittedTasks))
            .andReturn();
    submittedTasks.get(0).run();

    // then: 새 이벤트가 과거 이력을 앞지르지 않고 각 순번을 한 번만 전송한다.
    assertThat(
            result
                .getResponse()
                .getContentAsString()
                .lines()
                .filter(line -> line.startsWith("id:"))
                .toList())
        .containsExactly("id:1", "id:2");
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("대기분 전송 중 추가된 이벤트도 중복 없이 순서대로 보내고 실시간 전송으로 넘어간다")
  void event_arriving_while_draining_is_sent_once_before_live_delivery() throws Exception {
    // given: 과거 1번 뒤에 대기 2번을 보내고, 2번을 쓰는 동안 3번이 도착한다.
    dispatch_event(
        DISPATCH_JOB_ID,
        EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
        1L);
    var secondEvent =
        EventStreamTestFixtures.publishRequest(UUID.randomUUID(), INCIDENT_ID, "PATH_APPENDED");
    UUID secondJobId = UUID.randomUUID();
    var flushCount = new AtomicInteger();
    var interleavingMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilter(
                (request, response, chain) -> {
                  var intercepted = spy((HttpServletResponse) response);
                  doAnswer(
                          invocation -> {
                            var value = invocation.callRealMethod();
                            int writtenFrames = flushCount.incrementAndGet();
                            if (writtenFrames == 1) { // 연결 확인 주석을 보낸 직후다.
                              dispatch_event(secondJobId, secondEvent, 2L);
                            } else if (writtenFrames == 3) { // 1번 이력 뒤의 대기 2번을 보내는 중이다.
                              dispatch_event(secondJobId, secondEvent, 2L);
                              dispatch_event(
                                  UUID.randomUUID(),
                                  EventStreamTestFixtures.publishRequest(
                                      UUID.randomUUID(), INCIDENT_ID, "PATH_APPENDED"),
                                  3L);
                            }
                            return value;
                          })
                      .when(intercepted)
                      .flushBuffer();
                  chain.doFilter(request, intercepted);
                })
            .build();
    List<Runnable> submittedTasks = new ArrayList<>();
    var result =
        interleavingMvc
            .perform(context -> new DeferredStartRequest(context, submittedTasks))
            .andReturn();

    // when: 재전송을 마친 뒤 4번은 일반 실시간 경로로 보낸다.
    submittedTasks.get(0).run();
    dispatch_event(
        UUID.randomUUID(),
        EventStreamTestFixtures.publishRequest(UUID.randomUUID(), INCIDENT_ID, "PATH_APPENDED"),
        4L);
    submittedTasks.get(1).run();

    // then: 중복 2번과 전환 시점의 새 이벤트가 순서를 깨뜨리거나 누락을 만들지 않는다.
    assertThat(
            result
                .getResponse()
                .getContentAsString()
                .lines()
                .filter(line -> line.startsWith("id:"))
                .toList())
        .containsExactly("id:1", "id:2", "id:3", "id:4");
  }

  @ParameterizedTest(name = "실시간: {0}, 대기 이벤트: {1}개")
  @CsvSource({"false,1000", "false,1001", "true,1000", "true,1001"})
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("재전송·실시간 대기 이벤트가 1,000개를 넘으면 해당 연결만 끊고 이력은 보존한다")
  void pending_event_limit_disconnects_only_overflowing_connection(boolean live, int eventCount)
      throws Exception {
    // given: 한 연결은 재전송 중이고 다른 연결은 실시간 이벤트를 받는다.
    List<SseEventFrame> receivedByOtherConnection = new ArrayList<>();
    connectionRegistry.registerForIncident(INCIDENT_ID, receivedByOtherConnection::add);
    Runnable enqueueEvents =
        () -> {
          for (int sequence = 1; sequence <= eventCount; sequence++) {
            dispatch_event(
                UUID.randomUUID(),
                EventStreamTestFixtures.publishRequest(
                    UUID.randomUUID(), INCIDENT_ID, "PATH_APPENDED"),
                sequence);
          }
        };
    var interleavingMvc =
        interceptFirstResponseWrite(
            () -> {
              if (!live) {
                enqueueEvents.run();
              }
            });
    List<Runnable> submittedTasks = new ArrayList<>();
    var result =
        interleavingMvc
            .perform(context -> new DeferredStartRequest(context, submittedTasks))
            .andReturn();

    // when: 재전송 응답을 쓰는 사이 새 이벤트들이 도착한다.
    submittedTasks.get(0).run();
    if (live) {
      enqueueEvents.run();
      submittedTasks.get(1).run();
    }

    // then: 한도까지는 모두 이어 보내고, 초과하면 그 연결만 제거한다.
    assertThat(receivedByOtherConnection).hasSize(eventCount);
    assertThat(replayStore.findByIncidentId(INCIDENT_ID)).hasSize(eventCount);
    if (eventCount == 1000) {
      assertThat(connectionRegistry.sinks(INCIDENT_ID)).hasSize(2);
      assertThat(
              result
                  .getResponse()
                  .getContentAsString()
                  .lines()
                  .filter(line -> line.startsWith("id:"))
                  .toList())
          .hasSize(1000);
    } else {
      assertThat(connectionRegistry.sinks(INCIDENT_ID)).hasSize(1);
      assertThat(result.getResponse().getContentAsString()).doesNotContain("id:");
    }
  }

  @ParameterizedTest(name = "실시간 {0}, 이벤트 {1}개, 한글 {2}자, 초과 {3}")
  @CsvSource({
    "false,1,340000,false",
    "false,1,350000,true",
    "false,2,180000,true",
    "true,1,340000,false",
    "true,1,350000,true",
    "true,2,180000,true"
  })
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("재전송·실시간 대기는 글자 수가 아닌 UTF-8 전송 바이트 합계가 1MiB를 넘을 때 종료한다")
  void pending_byte_limit_counts_serialized_utf8_frames(
      boolean live, int eventCount, int characterCount, boolean overflow) throws Exception {
    // given: 개수 한도보다 작지만 한글 내용 때문에 전송 바이트가 큰 이벤트들이다.
    Runnable enqueueEvents =
        () -> {
          for (int sequence = 1; sequence <= eventCount; sequence++) {
            dispatch_event(
                UUID.randomUUID(),
                EventStreamTestFixtures.publishRequest(
                    UUID.randomUUID(),
                    INCIDENT_ID,
                    "PATH_APPENDED",
                    "30000000-0000-4000-8000-000000000501",
                    "가".repeat(characterCount),
                    7L),
                sequence);
          }
        };
    var interleavingMvc =
        interceptFirstResponseWrite(
            () -> {
              if (!live) {
                enqueueEvents.run();
              }
            });
    List<Runnable> submittedTasks = new ArrayList<>();
    var result =
        interleavingMvc
            .perform(context -> new DeferredStartRequest(context, submittedTasks))
            .andReturn();

    // when: 과거 이력의 전송이 끝나기 전에 새 이벤트가 도착한다.
    submittedTasks.get(0).run();
    if (live) {
      enqueueEvents.run();
      submittedTasks.get(1).run();
    }

    // then: 직렬화된 합계가 한도 안이면 전송하고, 초과하면 이력을 보존한 채 종료한다.
    assertThat(replayStore.findByIncidentId(INCIDENT_ID)).hasSize(eventCount);
    if (overflow) {
      assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
      assertThat(result.getResponse().getContentAsString()).doesNotContain("id:");
    } else {
      assertThat(connectionRegistry.sinks(INCIDENT_ID)).hasSize(1);
      assertThat(result.getResponse().getContentAsString()).contains("id:1");
    }
  }

  @ParameterizedTest(name = "종료 이벤트: {0}")
  @ValueSource(strings = {"INCIDENT_CLOSED", "INCIDENT_PURGED"})
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("재전송 중 사건 종료·파기가 도착하면 남은 과거 내용 대신 종료 정보만 보내고 닫는다")
  void terminal_event_during_replay_discards_pending_history_and_closes(String eventType)
      throws Exception {
    // given: 아직 보내지 않은 경로 이력이 있으며 첫 응답 쓰기 중 종료 정보가 도착한다.
    dispatch_event(
        DISPATCH_JOB_ID,
        EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
        1L);
    var interleavingMvc =
        interceptFirstResponseWrite(
            () ->
                dispatch_event(
                    UUID.randomUUID(),
                    EventStreamTestFixtures.publishRequest(
                        UUID.randomUUID(), INCIDENT_ID, eventType),
                    2L));
    List<Runnable> submittedTasks = new ArrayList<>();
    var result =
        interleavingMvc
            .perform(context -> new DeferredStartRequest(context, submittedTasks))
            .andReturn();

    // when: 과거 이력 전송을 시작한다.
    submittedTasks.get(0).run();

    // then: 종료를 확인한 뒤 이전 경로 내용을 새로 내보내지 않는다.
    assertThat(result.getResponse().getContentAsString())
        .contains("id:2", "event:" + eventType)
        .doesNotContain("id:1", "event:PATH_APPENDED");
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
    assertThat(WebAsyncUtils.getAsyncManager(result.getRequest()).hasConcurrentResult()).isTrue();
    assertThat(result.getAsyncResult()).isNull();
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("재전송 응답 쓰기가 막혀도 다른 연결은 전달받고 초과 연결의 등록은 즉시 제거된다")
  void blocked_replay_write_does_not_block_other_connections_or_overflow_cleanup()
      throws Exception {
    // given: 한 연결의 실제 응답 쓰기를 멈추고 다른 연결은 정상 수신한다.
    var writeStarted = new CountDownLatch(1);
    var releaseWrite = new CountDownLatch(1);
    var otherReceived = new AtomicInteger();
    connectionRegistry.registerForIncident(INCIDENT_ID, ignored -> otherReceived.incrementAndGet());
    var slowMvc =
        interceptFirstResponseWrite(
            () -> {
              writeStarted.countDown();
              assertThat(releaseWrite.await(10, TimeUnit.SECONDS)).isTrue();
            });
    List<Runnable> submittedTasks = new CopyOnWriteArrayList<>();
    var result =
        slowMvc.perform(context -> new DeferredStartRequest(context, submittedTasks)).andReturn();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var replay = executor.submit(submittedTasks.get(0));
      assertThat(writeStarted.await(10, TimeUnit.SECONDS)).isTrue();

      // when: 재전송 스레드와 다른 스레드에서 새 이벤트를 보낸다.
      var delivery =
          executor.submit(
              () -> {
                for (int sequence = 1; sequence <= 1001; sequence++) {
                  dispatch_event(
                      UUID.randomUUID(),
                      EventStreamTestFixtures.publishRequest(
                          UUID.randomUUID(), INCIDENT_ID, "PATH_APPENDED"),
                      sequence);
                }
              });
      delivery.get(10, TimeUnit.SECONDS);

      // then: 막힌 쓰기를 풀기 전에도 정상 연결의 전달과 초과 연결 해제가 끝난다.
      assertThat(otherReceived.get()).isEqualTo(1001);
      assertThat(connectionRegistry.sinks(INCIDENT_ID)).hasSize(1);
      assertThat(submittedTasks).hasSize(2);
      releaseWrite.countDown();
      replay.get(10, TimeUnit.SECONDS);
      submittedTasks.get(1).run();
      assertThat(result.getResponse().getContentAsString()).doesNotContain("id:");
      assertThat(result.getAsyncResult()).isNull();
    } finally {
      releaseWrite.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @ParameterizedTest(name = "실시간 쓰기 중단: {0}")
  @ValueSource(booleans = {false, true})
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("실시간 응답 쓰기가 막혀도 다른 상황판에는 이벤트를 전달한다")
  void blocked_live_write_does_not_block_other_connections(boolean blockLiveWrite)
      throws Exception {
    // given: 재전송을 마친 연결의 실시간 응답 쓰기만 멈춘다.
    var blockWrite = new AtomicBoolean();
    var writeStarted = new CountDownLatch(1);
    var releaseWrite = new CountDownLatch(1);
    var otherReceived = new CountDownLatch(1);
    var slowMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilter(
                (request, response, chain) -> {
                  var intercepted = spy((HttpServletResponse) response);
                  doAnswer(
                          invocation -> {
                            if (blockWrite.get()) {
                              writeStarted.countDown();
                              assertThat(releaseWrite.await(10, TimeUnit.SECONDS)).isTrue();
                            }
                            return invocation.callRealMethod();
                          })
                      .when(intercepted)
                      .flushBuffer();
                  chain.doFilter(request, intercepted);
                })
            .build();
    var executor = Executors.newFixedThreadPool(2);
    try {
      List<Runnable> tasks = new ArrayList<>();
      var livePhase = new AtomicBoolean();
      slowMvc
          .perform(
              context ->
                  new DeferredStartRequest(
                      context,
                      task -> {
                        if (livePhase.get()) {
                          executor.execute(task);
                        } else {
                          tasks.add(task);
                        }
                      }))
          .andReturn();
      tasks.get(0).run();
      livePhase.set(true);
      connectionRegistry.registerForIncident(INCIDENT_ID, ignored -> otherReceived.countDown());
      assertThat(connectionRegistry.sinks(INCIDENT_ID)).hasSize(2);
      blockWrite.set(blockLiveWrite);
      // when: 과거 이력 전송 이후 처음 도착한 실시간 이벤트를 전달한다.
      var delivery =
          executor.submit(
              () ->
                  dispatch_event(
                      DISPATCH_JOB_ID,
                      EventStreamTestFixtures.publishRequest(
                          EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
                      1L));
      if (blockLiveWrite) {
        assertThat(writeStarted.await(10, TimeUnit.SECONDS)).isTrue();
      }

      // then: 느린 쓰기를 풀지 않아도 정상 연결의 전달이 끝나야 한다. 1초는 테스트 대기 한도다.
      assertThat(otherReceived.await(1, TimeUnit.SECONDS))
          .as("느린 실시간 연결이 정상 연결의 전송을 막지 않아야 한다")
          .isTrue();
      releaseWrite.countDown();
      delivery.get(10, TimeUnit.SECONDS);
    } finally {
      releaseWrite.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      assertThat(otherReceived.getCount()).isZero();
    }
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("대기량 초과로 끊겨도 마지막으로 받은 순번부터 재접속하면 나머지 이벤트를 빠짐없이 받는다")
  void reconnect_after_overflow_resumes_after_last_received_sequence() throws Exception {
    // given: 첫 이벤트 전송 직후 새 이벤트 1,001개가 도착하도록 준비한다.
    dispatch_event(
        DISPATCH_JOB_ID,
        EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
        1L);
    var flushCount = new AtomicInteger();
    var interleavingMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilter(
                (request, response, chain) -> {
                  var intercepted = spy((HttpServletResponse) response);
                  doAnswer(
                          invocation -> {
                            var value = invocation.callRealMethod();
                            if (flushCount.incrementAndGet() == 2) {
                              for (int sequence = 2; sequence <= 1002; sequence++) {
                                dispatch_event(
                                    UUID.randomUUID(),
                                    EventStreamTestFixtures.publishRequest(
                                        UUID.randomUUID(), INCIDENT_ID, "PATH_APPENDED"),
                                    sequence);
                              }
                            }
                            return value;
                          })
                      .when(intercepted)
                      .flushBuffer();
                  chain.doFilter(request, intercepted);
                })
            .build();
    List<Runnable> submittedTasks = new ArrayList<>();
    var first =
        interleavingMvc
            .perform(context -> new DeferredStartRequest(context, submittedTasks))
            .andReturn();
    submittedTasks.get(0).run();
    assertThat(
            first
                .getResponse()
                .getContentAsString()
                .lines()
                .filter(line -> line.startsWith("id:"))
                .toList())
        .containsExactly("id:1");
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
    submittedTasks.get(1).run();

    // when: 실제 받은 1번을 마지막 수신 순번으로 전달해 다시 접속한다.
    List<Runnable> resumedTasks = new ArrayList<>();
    var resumed =
        mockMvc
            .perform(
                context -> {
                  var request = new DeferredStartRequest(context, resumedTasks);
                  request.addHeader("Last-Event-ID", "1");
                  return request;
                })
            .andReturn();
    resumedTasks.get(0).run();

    // then: 대기에 넣었던 순번을 건너뛰지 않고 2번부터 1,002번까지 이어 보낸다.
    var receivedIds =
        resumed
            .getResponse()
            .getContentAsString()
            .lines()
            .filter(line -> line.startsWith("id:"))
            .toList();
    assertThat(receivedIds).hasSize(1001).startsWith("id:2").endsWith("id:1002");
    for (int index = 0; index < receivedIds.size(); index++) {
      assertThat(receivedIds.get(index)).isEqualTo("id:" + (index + 2));
    }
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
    var otherIncident = UUID.randomUUID();
    connectionRegistry.sendToAccount(
        accountId,
        new SseEventFrame(
            "1",
            "INCIDENT_CREATED",
            EventStreamTestFixtures.publishRequest(
                UUID.randomUUID(), otherIncident, "INCIDENT_CREATED")));
    assertThat(submittedTasks).hasSize(2);
    submittedTasks.get(1).run();
    String received = result.getResponse().getContentAsString();
    assertThat(received).contains(":connected", "id:1", "event:INCIDENT_CREATED");
    assertThat(received).contains(INCIDENT_ID.toString(), otherIncident.toString());
    assertThat(received.lines().filter(line -> line.equals("id:1")).count()).isEqualTo(2);
    var context = (MockAsyncContext) result.getRequest().getAsyncContext();
    for (var listener : context.getListeners()) {
      listener.onComplete(new AsyncEvent(context));
    }

    // then: 종료 뒤 다시 보내도 응답은 바뀌지 않는다.
    connectionRegistry.sendToAccount(accountId, frame);
    assertThat(result.getResponse().getContentAsString()).isEqualTo(received);
  }

  @Test
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("다음 페이지 조회 중 파기가 확정돼도 대기 중인 파기 알림을 전달하고 닫는다")
  void purge_during_next_page_read_delivers_pending_terminal_event() throws Exception {
    // given: 과거 이력은 두 페이지이며 두 번째 조회 중 파기 알림이 도착한다.
    for (long sequence = 1; sequence <= 101; sequence++) {
      dispatch_event(
          UUID.randomUUID(),
          EventStreamTestFixtures.publishRequest(UUID.randomUUID(), INCIDENT_ID, "PATH_APPENDED"),
          sequence);
    }
    doAnswer(
            invocation -> {
              dispatch_event(
                  UUID.randomUUID(),
                  EventStreamTestFixtures.publishRequest(
                      UUID.randomUUID(), INCIDENT_ID, "INCIDENT_PURGED"),
                  102);
              throw new GoneRefetchRequiredException();
            })
        .when(replayService)
        .replayNextPage(any(), any());
    List<Runnable> tasks = new ArrayList<>();
    var result = mockMvc.perform(context -> new DeferredStartRequest(context, tasks)).andReturn();

    // when: 초기 전송과 다음 페이지 조회를 실행한다.
    tasks.get(0).run();

    // then: 남은 과거 이력은 보내지 않고 파기 알림을 보낸 뒤 정상 종료한다.
    assertThat(result.getResponse().getContentAsString())
        .contains("id:102", "event:INCIDENT_PURGED")
        .doesNotContain("id:101\n");
    assertThat(result.getAsyncResult()).isNull();
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
  }

  @ParameterizedTest(name = "쓰기 대기 위치: {0}")
  @ValueSource(strings = {"replay", "live"})
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("페이지 조회·실시간 대기 이후 사건 접근이 거부되면 이전 내용을 쓰지 않고 연결을 닫는다")
  void terminal_state_before_queued_write_blocks_payload_and_closes_connection(String phase)
      throws Exception {
    // given: 조회·발행 때는 허용되지만 실제 쓰기 전에 종료·파기가 확정된다.
    var terminal = new AtomicBoolean();
    doAnswer(
            invocation -> {
              if (terminal.get()) {
                throw new GoneRefetchRequiredException();
              }
              return null;
            })
        .when(jobService)
        .validateSseTransmission(INCIDENT_ID);
    if (phase.equals("replay")) {
      dispatch_event(
          DISPATCH_JOB_ID,
          EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
          1L);
      var snapshot = replay_page(0, 1);
      when(replayService.replayResultAfter(any(), nullable(String.class)))
          .thenReturn(snapshot)
          .thenAnswer(
              invocation -> {
                terminal.set(true);
                return snapshot;
              });
    }
    List<Runnable> tasks = new ArrayList<>();
    var result = mockMvc.perform(context -> new DeferredStartRequest(context, tasks)).andReturn();

    // when: 조회 결과를 가져온 뒤 또는 실시간 쓰기 작업을 시작하기 전에 상태가 바뀐다.
    tasks.get(0).run();
    if (phase.equals("live")) {
      dispatch_event(
          DISPATCH_JOB_ID,
          EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
          1L);
      terminal.set(true);
      tasks.get(1).run();
    }

    // then: 과거 내용은 쓰지 않고 재접속에서 최신 종료 상태를 확인하도록 정리한다.
    assertThat(result.getResponse().getContentAsString()).doesNotContain("event:PATH_APPENDED");
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
    assertThat(result.getAsyncResult()).isNull();
  }

  @ParameterizedTest(name = "종료 알림: {0}")
  @ValueSource(strings = {"INCIDENT_CLOSED", "INCIDENT_PURGED"})
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("실시간 쓰기 중 종료·파기 알림이 오면 이미 시작한 쓰기 뒤의 대기 내용을 버리고 종료만 보낸다")
  void terminal_event_discards_pending_live_payload_and_closes_after_delivery(String eventType)
      throws Exception {
    // given: 1번 쓰기는 이미 시작했고 2번은 아직 대기 중이다.
    var blockNextWrite = new AtomicBoolean();
    var writeStarted = new CountDownLatch(1);
    var releaseWrite = new CountDownLatch(1);
    var slowMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilter(
                (request, response, chain) -> {
                  var intercepted = spy((HttpServletResponse) response);
                  doAnswer(
                          invocation -> {
                            if (blockNextWrite.compareAndSet(true, false)) {
                              writeStarted.countDown();
                              assertThat(releaseWrite.await(10, TimeUnit.SECONDS)).isTrue();
                            }
                            return invocation.callRealMethod();
                          })
                      .when(intercepted)
                      .flushBuffer();
                  chain.doFilter(request, intercepted);
                })
            .build();
    List<Runnable> tasks = new ArrayList<>();
    var result = slowMvc.perform(context -> new DeferredStartRequest(context, tasks)).andReturn();
    tasks.get(0).run();
    blockNextWrite.set(true);
    dispatch_event(
        DISPATCH_JOB_ID,
        EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
        1L);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var write = executor.submit(tasks.get(1));
      assertThat(writeStarted.await(10, TimeUnit.SECONDS)).isTrue();
      dispatch_event(
          UUID.randomUUID(),
          EventStreamTestFixtures.publishRequest(UUID.randomUUID(), INCIDENT_ID, "PATH_APPENDED"),
          2L);

      // when: 종료 알림을 대기시킨 뒤 이미 시작한 1번 쓰기를 마친다.
      dispatch_event(
          UUID.randomUUID(),
          EventStreamTestFixtures.publishRequest(UUID.randomUUID(), INCIDENT_ID, eventType),
          3L);
      releaseWrite.countDown();
      write.get(10, TimeUnit.SECONDS);

      // then: 2번은 쓰지 않고 3번 종료 알림 뒤 응답과 등록을 정리한다.
      assertThat(
              result
                  .getResponse()
                  .getContentAsString()
                  .lines()
                  .filter(line -> line.startsWith("id:"))
                  .toList())
          .containsExactly("id:1", "id:3");
      assertThat(result.getResponse().getContentAsString()).contains("event:" + eventType);
      assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
      assertThat(result.getAsyncResult()).isNull();
      assertThat(tasks).hasSize(2);
    } finally {
      releaseWrite.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @ParameterizedTest(name = "실시간 실패: {0}")
  @ValueSource(strings = {"io", "runtime", "rejected"})
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("실시간 쓰기·작업 제출이 실패하면 연결을 제거하고 내부 오류와 I/O 종료를 구분한다")
  void live_write_or_submission_failure_removes_connection(String failureType) throws Exception {
    // given: 초기 연결은 성공하고 실시간 쓰기 또는 작업 제출만 실패한다.
    var live = new AtomicBoolean();
    var failingMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilter(
                (request, response, chain) -> {
                  var intercepted = spy((HttpServletResponse) response);
                  doAnswer(
                          invocation -> {
                            if (live.get()) {
                              if (failureType.equals("io")) {
                                throw new IOException("test disconnect");
                              }
                              throw new IllegalArgumentException("test write failure");
                            }
                            return invocation.callRealMethod();
                          })
                      .when(intercepted)
                      .flushBuffer();
                  chain.doFilter(request, intercepted);
                })
            .build();
    List<Runnable> tasks = new ArrayList<>();
    var result =
        failingMvc
            .perform(
                context ->
                    new DeferredStartRequest(
                        context,
                        task -> {
                          if (live.get() && failureType.equals("rejected")) {
                            throw new RejectedExecutionException("test rejected task");
                          }
                          tasks.add(task);
                        }))
            .andReturn();
    tasks.get(0).run();

    // when: 실시간 전송 작업을 실행한다.
    live.set(true);
    dispatch_event(
        DISPATCH_JOB_ID,
        EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
        1L);
    if (!failureType.equals("rejected")) {
      tasks.get(1).run();
    }

    // then: I/O 정리는 컨테이너에 맡기고 내부 오류는 오류 완료로 전달한다.
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
    assertThat(WebAsyncUtils.getAsyncManager(result.getRequest()).hasConcurrentResult())
        .isEqualTo(!failureType.equals("io"));
    if (!failureType.equals("io")) {
      assertThat(result.getAsyncResult()).isInstanceOf(RuntimeException.class);
    }
  }

  @ParameterizedTest(name = "상태 조회 중 도착한 종료 알림: {0}")
  @ValueSource(strings = {"INCIDENT_CLOSED", "INCIDENT_PURGED"})
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MISSING_TEAM_COMMANDER)
  @DisplayName("대기분의 상태 조회 중 종료·파기가 확정되면 과거 내용 대신 도착한 종료 알림을 보낸다")
  void terminal_event_during_pending_validation_is_delivered_before_close(String eventType)
      throws Exception {
    // given: 실시간 이벤트가 대기 중이고, 쓰기 직전 상태 조회에서 종료 알림이 도착한다.
    List<Runnable> tasks = new ArrayList<>();
    var result = mockMvc.perform(context -> new DeferredStartRequest(context, tasks)).andReturn();
    tasks.get(0).run();
    dispatch_event(
        DISPATCH_JOB_ID,
        EventStreamTestFixtures.publishRequest(EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
        1L);
    doAnswer(
            invocation -> {
              dispatch_event(
                  UUID.randomUUID(),
                  EventStreamTestFixtures.publishRequest(UUID.randomUUID(), INCIDENT_ID, eventType),
                  2L);
              throw new GoneRefetchRequiredException();
            })
        .when(jobService)
        .validateSseTransmission(INCIDENT_ID);

    // when: 대기 이벤트의 쓰기를 시도한다.
    tasks.get(1).run();

    // then: 도착한 종료 알림까지 버리는 대신 그 알림만 보낸 뒤 정리한다.
    assertThat(result.getResponse().getContentAsString())
        .contains("id:2", "event:" + eventType)
        .doesNotContain("id:1", "event:PATH_APPENDED");
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
    assertThat(result.getAsyncResult()).isNull();
  }

  private ReplayResult replay_page(long cursor, long through) {
    return ReplayResult.builder()
        .frames(
            replayStore.replayAfter(INCIDENT_ID, cursor).stream()
                .filter(event -> event.replaySequence() <= through)
                .limit(100)
                .map(
                    event ->
                        new SseEventFrame(
                            Long.toString(event.replaySequence()),
                            event.envelope().type(),
                            event.envelope()))
                .toList())
        .throughSequence(through)
        .terminalReached(replayStore.terminalReplaySequence(INCIDENT_ID).isPresent())
        .build();
  }

  private void dispatch_event(UUID jobId, PublishRequest request, long sequence) {
    replayStore.save(
        SseReplayEvent.active(
            jobId,
            jobId,
            request.incidentId(),
            sequence,
            request,
            EventStreamTestFixtures.CREATED_AT));
    streamService.dispatchLive(request, sequence);
    if ("INCIDENT_PURGED".equals(request.type())) {
      replayStore.purgeIncident(request.incidentId());
    }
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

  private MockMvc interceptFirstResponseWrite(Executable firstWriteAction) {
    var firstWrite = new AtomicBoolean();
    return MockMvcBuilders.webAppContextSetup(applicationContext)
        .addFilter(
            (request, response, chain) -> {
              var intercepted = spy((HttpServletResponse) response);
              doAnswer(
                      invocation -> {
                        if (firstWrite.compareAndSet(false, true)) {
                          firstWriteAction.execute();
                        }
                        return invocation.callRealMethod();
                      })
                  .when(intercepted)
                  .getOutputStream();
              chain.doFilter(request, intercepted);
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
