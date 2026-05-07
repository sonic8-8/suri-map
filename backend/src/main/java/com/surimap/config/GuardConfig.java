package com.surimap.config;

import com.surimap.common.auth.guard.GuardExceptionHandler;
import com.surimap.common.auth.guard.GuardInterceptor;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link GuardInterceptor} and the {@link GuardExceptionHandler} for all requests.
 *
 * <p>Port implementations ({@link IncidentAccessPort}, {@link PolicePhoneValidationPort}) must be
 * provided by the application context — either by a real database-backed bean in production or by
 * {@code GuardPortTestStubs} (test-only {@code @TestConfiguration}) in tests.
 */
@Configuration
@Import({GuardExceptionHandler.class})
public class GuardConfig implements WebMvcConfigurer {

  private final IncidentAccessPort incidentAccessPort;
  private final PolicePhoneValidationPort policePhoneValidationPort;

  public GuardConfig(
      IncidentAccessPort incidentAccessPort, PolicePhoneValidationPort policePhoneValidationPort) {
    this.incidentAccessPort = incidentAccessPort;
    this.policePhoneValidationPort = policePhoneValidationPort;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new GuardInterceptor(incidentAccessPort, policePhoneValidationPort));
  }
}
