package com.surimap.global.sse;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.async.DeferredResult;

/** MVC가 비동기 응답 수명을 관리하고 Servlet이 쓸 수 있을 때만 SSE를 전송한다. */
public final class ServerSentEventStream extends DeferredResult<Void> {
  public static final String REQUEST_ATTRIBUTE = ServerSentEventStream.class.getName();
  private static final Logger log = LoggerFactory.getLogger(ServerSentEventStream.class);
  private static final byte[] HEARTBEAT = ":heartbeat\n\n".getBytes(StandardCharsets.UTF_8);

  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean scheduled = new AtomicBoolean();
  private final AtomicBoolean requested = new AtomicBoolean();
  private final AtomicReference<AutoCloseable> registration = new AtomicReference<>();
  private final AtomicReference<ScheduledFuture<?>> heartbeatTask = new AtomicReference<>();
  private final AtomicBoolean heartbeatPending = new AtomicBoolean();
  private volatile AsyncContext context;
  private volatile Runnable transmission;
  // 아래 쓰기 상태는 연결마다 하나뿐인 전송 작업에서만 접근한다.
  private ServletOutputStream output;
  private boolean frameWritten;
  private boolean frameFlushed;
  private boolean heartbeatWriting;

  public ServerSentEventStream(Consumer<ServerSentEventStream> initialize) {
    super(0L);
    transmission = () -> initialize.accept(this);
    onCompletion(this::unregister);
    onTimeout(this::complete);
    onError(ignored -> unregister());
  }

  public void start(AsyncContext asyncContext, ScheduledExecutorService heartbeatScheduler) {
    context = asyncContext;
    // WHATWG 작성 참고의 초기값이다. 네트워크 장애 감지·복구 시간 보장은 아니다.
    heartbeatTask.set(
        heartbeatScheduler.scheduleWithFixedDelay(
            this::requestHeartbeat, 15, 15, TimeUnit.SECONDS));
    if (closed.get()) {
      cancelHeartbeat();
      return;
    }
    requestTransmission();
  }

  public boolean attachRegistration(AutoCloseable newRegistration) {
    registration.set(newRegistration);
    if (closed.get()) {
      unregister();
      return false;
    }
    return true;
  }

  public void complete() {
    unregister();
    setResult(null);
  }

  public void completeWithError(Throwable failure) {
    unregister();
    log.warn("SSE stream failed. failureType={}", failure.getClass().getSimpleName());
    setErrorResult(failure);
  }

  void continueTransmission(Runnable sendAvailableEvents) {
    if (!closed.get()) {
      transmission = sendAvailableEvents;
      sendAvailableEvents.run();
    }
  }

  void requestTransmission() {
    if (closed.get()) {
      return;
    }
    requested.set(true);
    if (!scheduled.compareAndSet(false, true)) {
      return;
    }
    try {
      context.start(this::sendAvailableEvents);
    } catch (RuntimeException exception) {
      scheduled.set(false);
      completeWithError(exception);
    }
  }

  boolean isReady() {
    if (closed.get()) {
      return false;
    }
    try {
      if (output == null) {
        output = context.getResponse().getOutputStream();
        output.setWriteListener(
            new WriteListener() {
              @Override
              public void onWritePossible() {
                requestTransmission();
              }

              @Override
              public void onError(Throwable failure) {
                log.debug("SSE socket failed. failureType={}", failure.getClass().getSimpleName());
                complete();
              }
            });
      }
      return !closed.get() && output.isReady();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  boolean writeFrame(byte[] bytes) {
    try {
      if (!isReady()) {
        return false;
      }
      if (!frameWritten) {
        frameWritten = true;
        output.write(bytes);
      }
      if (!isReady()) {
        return false;
      }
      if (!frameFlushed) {
        frameFlushed = true;
        context.getResponse().flushBuffer();
      }
      if (!isReady()) {
        return false;
      }
      frameWritten = false;
      frameFlushed = false;
      return true;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  boolean hasFrameStarted() {
    return frameWritten;
  }

  boolean isClosed() {
    return closed.get();
  }

  private void unregister() {
    closed.set(true);
    cancelHeartbeat();
    transmission = null;
    AutoCloseable current = registration.getAndSet(null);
    if (current != null) {
      try {
        current.close();
      } catch (Exception exception) {
        log.warn(
            "SSE registration cleanup failed. failureType={}",
            exception.getClass().getSimpleName());
      }
    }
  }

  private void requestHeartbeat() {
    if (!closed.get()) {
      // 느린 연결에도 주석을 누적하지 않고 한 번의 전송 요청만 유지한다.
      heartbeatPending.set(true);
      requestTransmission();
    }
  }

  private void cancelHeartbeat() {
    ScheduledFuture<?> current = heartbeatTask.getAndSet(null);
    if (current != null) {
      current.cancel(false);
    }
  }

  private void sendAvailableEvents() {
    try {
      do {
        requested.set(false);
        Runnable current = transmission;
        if (closed.get() || current == null) {
          return;
        }
        // 앞서 시작한 주석 쓰기·flush를 끝낸 뒤 업무 이벤트를 전송한다.
        if (heartbeatWriting) {
          if (!writeFrame(HEARTBEAT)) {
            return;
          }
          heartbeatWriting = false;
        }
        current.run();
        if (!closed.get() && !hasFrameStarted() && heartbeatPending.compareAndSet(true, false)) {
          heartbeatWriting = true;
          if (!writeFrame(HEARTBEAT)) {
            return;
          }
          heartbeatWriting = false;
        }
        // 쓸 수 없으면 기다리지 않고 다음 전송 가능 알림이나 이벤트 도착 때 재개한다.
      } while (requested.get() && !closed.get());
    } catch (ServerSentEventRefetchRequiredException exception) {
      complete();
    } catch (UncheckedIOException exception) {
      log.debug("SSE write failed. failureType={}", exception.getClass().getSimpleName());
      complete();
    } catch (RuntimeException exception) {
      completeWithError(exception);
    } finally {
      scheduled.set(false);
      // 작업이 끝나는 순간 도착한 전송 요청도 놓치지 않는다.
      if (requested.get() && !closed.get()) {
        requestTransmission();
      }
    }
  }
}
