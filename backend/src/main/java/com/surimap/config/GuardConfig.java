package com.surimap.config;

import com.surimap.common.auth.guard.GuardExceptionHandler;
import com.surimap.common.auth.guard.GuardInterceptor;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.common.auth.guard.IncidentAccessDeniedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link GuardInterceptor} and the {@link GuardExceptionHandler} for all requests.
 *
 * <p>The {@link IncidentAccessPort} implementation is optional at context startup so unrelated
 * controller slices can load. If a guarded endpoint needs the missing port, the fallback denies the
 * request.
 */
@Configuration
@Import({GuardExceptionHandler.class})
public class GuardConfig implements WebMvcConfigurer {

  private final IncidentAccessPort incidentAccessPort;

  public GuardConfig(ObjectProvider<IncidentAccessPort> incidentAccessPort) {
    this.incidentAccessPort = incidentAccessPort.getIfAvailable(GuardConfig::denyIncidentAccess);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new GuardInterceptor(incidentAccessPort));
  }

  private static IncidentAccessPort denyIncidentAccess() {
    return auth -> {
      throw new IncidentAccessDeniedException();
    };
  }
}
