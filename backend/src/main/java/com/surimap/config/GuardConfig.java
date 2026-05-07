package com.surimap.config;

import com.surimap.common.auth.guard.GuardExceptionHandler;
import com.surimap.common.auth.guard.GuardInterceptor;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.common.auth.guard.IncidentAccessDeniedException;
import com.surimap.common.auth.guard.PolicePhoneNotAssignedException;
import com.surimap.common.auth.guard.PolicePhoneNotRegisteredException;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link GuardInterceptor} and the {@link GuardExceptionHandler} for all requests.
 *
 * <p>Port implementations ({@link IncidentAccessPort}, {@link PolicePhoneValidationPort}) are
 * optional at context startup so unrelated controller slices can load. If a guarded endpoint needs a
 * missing port, the fallback denies the request.
 */
@Configuration
@Import({GuardExceptionHandler.class})
public class GuardConfig implements WebMvcConfigurer {

  private final IncidentAccessPort incidentAccessPort;
  private final PolicePhoneValidationPort policePhoneValidationPort;

  public GuardConfig(
      ObjectProvider<IncidentAccessPort> incidentAccessPort,
      ObjectProvider<PolicePhoneValidationPort> policePhoneValidationPort) {
    this.incidentAccessPort = incidentAccessPort.getIfAvailable(GuardConfig::denyIncidentAccess);
    this.policePhoneValidationPort =
        policePhoneValidationPort.getIfAvailable(GuardConfig::denyPolicePhoneValidation);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new GuardInterceptor(incidentAccessPort, policePhoneValidationPort));
  }

  private static IncidentAccessPort denyIncidentAccess() {
    return auth -> {
      throw new IncidentAccessDeniedException();
    };
  }

  private static PolicePhoneValidationPort denyPolicePhoneValidation() {
    return new PolicePhoneValidationPort() {
      @Override
      public void checkRegistered(UUID policePhoneId) {
        throw new PolicePhoneNotRegisteredException();
      }

      @Override
      public void checkAssigned(UUID policePhoneId) {
        throw new PolicePhoneNotAssignedException();
      }
    };
  }
}
