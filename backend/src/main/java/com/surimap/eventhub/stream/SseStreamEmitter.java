package com.surimap.eventhub.stream;

import jakarta.servlet.AsyncContext;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** MVC가 응답 전송을 준비한 뒤 컨테이너 작업에서 이벤트를 보낸다. */
final class SseStreamEmitter extends SseEmitter {

  static final String REQUEST_ATTRIBUTE = SseStreamEmitter.class.getName();

  private static final Logger log = LoggerFactory.getLogger(SseStreamEmitter.class);

  private final Consumer<SseStreamEmitter> initialTransmission;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicReference<AutoCloseable> registration = new AtomicReference<>();
  private volatile AsyncContext asyncContext;

  SseStreamEmitter(Consumer<SseStreamEmitter> initialTransmission) {
    super(0L);
    this.initialTransmission = initialTransmission;
    onCompletion(this::unregister);
    onTimeout(this::unregister);
    onError(ignored -> unregister());
  }

  @Override
  public void complete() {
    unregister();
    super.complete();
  }

  @Override
  public void completeWithError(Throwable failure) {
    unregister();
    log.warn("SSE stream failed. failureType={}", failure.getClass().getSimpleName());
    super.completeWithError(failure);
  }

  void start(AsyncContext context) {
    asyncContext = context;
    submitTransmission(() -> initialTransmission.accept(this));
  }

  void submitTransmission(Runnable transmission) {
    try {
      // ponytail: 컨테이너의 기존 실행기를 쓴다. 다수의 느린 연결로 고갈되면 전용 실행기/비차단 쓰기를 검토한다.
      asyncContext.start(() -> sendEvents(transmission));
    } catch (RuntimeException exception) {
      completeWithError(exception);
    }
  }

  boolean isClosed() {
    return closed.get();
  }

  void disconnect() {
    unregister();
    // 재전송 스레드가 느린 소켓에 쓰는 중이어도 공용 전송 worker를 기다리게 하지 않는다.
    try {
      asyncContext.start(this::complete);
    } catch (RuntimeException exception) {
      log.warn("SSE close task rejected. failureType={}", exception.getClass().getSimpleName());
      asyncContext.complete();
    }
  }

  boolean attachRegistration(AutoCloseable newRegistration) {
    registration.set(newRegistration);
    if (closed.get()) {
      unregister();
      return false;
    }
    return true;
  }

  private void unregister() {
    closed.set(true);
    var current = registration.getAndSet(null);
    if (current == null) {
      return;
    }
    try {
      current.close();
    } catch (Exception exception) {
      log.warn(
          "SSE registration cleanup failed. failureType={}", exception.getClass().getSimpleName());
    }
  }

  private void sendEvents(Runnable transmission) {
    if (closed.get()) {
      return;
    }
    try {
      transmission.run();
    } catch (GoneRefetchRequiredException exception) {
      // 대기 중 종료·파기되거나 이력이 없어졌다. 재접속의 HTTP 검사로 최신 상태를 확인한다.
      complete();
    } catch (UncheckedIOException exception) {
      unregister();
      // HTTP I/O 오류의 응답 정리는 Servlet 컨테이너가 담당한다.
      log.debug("SSE write failed. failureType={}", exception.getClass().getSimpleName());
    } catch (RuntimeException exception) {
      completeWithError(exception);
    }
  }
}
