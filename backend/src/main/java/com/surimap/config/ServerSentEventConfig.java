package com.surimap.config;

import com.surimap.global.sse.ServerSentEventConnectionRegistry;
import com.surimap.global.sse.ServerSentEventStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Import(com.surimap.global.error.GlobalExceptionHandler.class)
public class ServerSentEventConfig implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    ScheduledExecutorService heartbeatScheduler = serverSentEventHeartbeatScheduler();
    registry.addInterceptor(
        new AsyncHandlerInterceptor() {
          @Override
          public void afterConcurrentHandlingStarted(
              HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (request.getAttribute(ServerSentEventStream.REQUEST_ATTRIBUTE)
                instanceof ServerSentEventStream stream) {
              request.removeAttribute(ServerSentEventStream.REQUEST_ATTRIBUTE);
              // 이 콜백은 MVC 반환 처리의 실패 경로에서도 호출될 수 있다.
              if (!request.isAsyncStarted()
                  || WebAsyncUtils.getAsyncManager(request).hasConcurrentResult()
                  || response.getStatus() >= 400
                  || request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE) != null) {
                stream.complete();
                return;
              }
              try {
                stream.start(request.getAsyncContext(), heartbeatScheduler);
              } catch (RuntimeException exception) {
                stream.completeWithError(exception);
              }
            }
          }
        });
  }

  @Bean
  ServerSentEventConnectionRegistry serverSentEventConnectionRegistry() {
    return new ServerSentEventConnectionRegistry();
  }

  @Bean(destroyMethod = "shutdownNow")
  ScheduledThreadPoolExecutor serverSentEventHeartbeatScheduler() {
    ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    // 종료한 연결을 다음 실행 시각까지 예약 큐에 붙잡아 두지 않는다.
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }
}
