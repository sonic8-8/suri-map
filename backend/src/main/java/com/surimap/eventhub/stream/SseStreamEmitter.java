package com.surimap.eventhub.stream;

import jakarta.servlet.AsyncContext;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** MVC가 응답 전송을 준비한 뒤 컨테이너 작업에서 초기 이벤트를 보낸다. */
final class SseStreamEmitter extends SseEmitter {

  static final String REQUEST_ATTRIBUTE = SseStreamEmitter.class.getName();

  private static final Logger log = LoggerFactory.getLogger(SseStreamEmitter.class);

  private final Consumer<SseStreamEmitter> initialTransmission;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicReference<AutoCloseable> registration = new AtomicReference<>();

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
    context.start(this::sendInitialEvents);
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

  private void sendInitialEvents() {
    if (closed.get()) {
      return;
    }
    try {
      initialTransmission.accept(this);
    } catch (UncheckedIOException exception) {
      unregister();
      // HTTP I/O 오류의 응답 정리는 Servlet 컨테이너가 담당한다.
      log.debug("SSE initial write failed. failureType={}", exception.getClass().getSimpleName());
    } catch (RuntimeException exception) {
      completeWithError(exception);
    }
  }
}
