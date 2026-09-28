package com.surimap.eventhub.stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Import(EventStreamExceptionHandler.class)
public class EventStreamConfig implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new AsyncHandlerInterceptor() {
          @Override
          public void afterConcurrentHandlingStarted(
              HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (request.getAttribute(SseStreamEmitter.REQUEST_ATTRIBUTE)
                instanceof SseStreamEmitter emitter) {
              request.removeAttribute(SseStreamEmitter.REQUEST_ATTRIBUTE);
              // 이 콜백은 MVC 반환 처리의 실패 경로에서도 호출될 수 있다.
              if (!request.isAsyncStarted()
                  || WebAsyncUtils.getAsyncManager(request).hasConcurrentResult()
                  || response.getStatus() >= 400
                  || request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE) != null) {
                emitter.complete();
                return;
              }
              try {
                emitter.start(request.getAsyncContext());
              } catch (RuntimeException exception) {
                emitter.completeWithError(exception);
              }
            }
          }
        });
  }

  @Bean
  SseConnectionRegistry sseConnectionRegistry() {
    return new SseConnectionRegistry();
  }
}
