package com.surimap.account.harness;

import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneAssigned;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.common.auth.RequireRole;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.GuardException;
import com.surimap.common.auth.guard.GuardInterceptor;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import java.lang.reflect.Method;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

/** Adapter that runs the same harness fixture through the real S1-2 guard interceptor. */
public final class RealS1_2AuthPolicePhoneContract implements AuthPolicePhoneContract {

  private final GuardInterceptor guardInterceptor;
  private final HandlerMethod appPolicePhoneHandler;
  private final HandlerMethod webCommandHandler;

  public RealS1_2AuthPolicePhoneContract(PolicePhoneValidationPort policePhoneValidationPort) {
    this.guardInterceptor = new GuardInterceptor(auth -> {}, policePhoneValidationPort);
    this.appPolicePhoneHandler = appPolicePhoneHandler();
    this.webCommandHandler = webCommandHandler();
  }

  @Override
  public AuthPolicePhoneHarnessFixtures.HarnessContext resolve(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture) {
    return fixture.context();
  }

  @Override
  public AuthPolicePhoneHarnessFixtures.GuardOutcome checkAppPolicePhone(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture) {
    return runGuard(fixture, appPolicePhoneHandler);
  }

  @Override
  public AuthPolicePhoneHarnessFixtures.GuardOutcome checkWebCommand(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture) {
    return runGuard(fixture, webCommandHandler);
  }

  private AuthPolicePhoneHarnessFixtures.GuardOutcome runGuard(
      AuthPolicePhoneHarnessFixtures.HarnessFixture fixture, HandlerMethod handlerMethod) {
    setSecurityContext(fixture.context());
    try {
      guardInterceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handlerMethod);
      return AuthPolicePhoneHarnessFixtures.GuardOutcome.ok();
    } catch (GuardException exception) {
      return AuthPolicePhoneHarnessFixtures.GuardOutcome.denied(exception.getErrorCode());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static void setSecurityContext(AuthPolicePhoneHarnessFixtures.HarnessContext context) {
    var authorities =
        context.authorities().stream().map(SimpleGrantedAuthority::new).toList();
    var authentication =
        new SuriMapAuthentication(
            context.accountCode(),
            context.accountType(),
            context.organizationType(),
            context.channel(),
            context.policePhoneId() == null ? null : context.policePhoneId().toString(),
            authorities);
    var securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(authentication);
    SecurityContextHolder.setContext(securityContext);
  }

  private static HandlerMethod appPolicePhoneHandler() {
    return handlerMethod("appPolicePhone");
  }

  private static HandlerMethod webCommandHandler() {
    return handlerMethod("webCommand");
  }

  private static HandlerMethod handlerMethod(String methodName) {
    try {
      Method method = GuardHarnessEndpoints.class.getDeclaredMethod(methodName);
      return new HandlerMethod(new GuardHarnessEndpoints(), method);
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException("guard harness endpoint must exist", exception);
    }
  }

  private static final class GuardHarnessEndpoints {

    @RequireChannel(com.surimap.common.auth.Channel.APP)
    @RequirePolicePhone
    @RequirePolicePhoneRegistered
    @RequirePolicePhoneAssigned
    void appPolicePhone() {}

    @RequireChannel(com.surimap.common.auth.Channel.WEB)
    @RequireRole
    void webCommand() {}
  }
}
