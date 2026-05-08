package com.surimap.common.auth.guard;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireDevice;
import com.surimap.common.auth.RequireDeviceAssigned;
import com.surimap.common.auth.RequireDeviceRegistered;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneAssigned;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.common.auth.RequireRole;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Intercepts requests and enforces guard annotations:
 *
 * <ul>
 *   <li>{@link RequireChannel} — channel must be in the allowed set
 *   <li>{@link RequireRole} — account must have a non-MEMBER role
 *   <li>{@link RequireIncidentAccess} — account must be assigned to the active incident
 *   <li>{@link RequirePolicePhone} — policePhoneId must be present in session (APP only)
 *   <li>{@link RequirePolicePhoneRegistered} — police phone must be registered (APP only)
 *   <li>{@link RequirePolicePhoneAssigned} — police phone must be assigned to incident (APP only)
 * </ul>
 *
 * <p>Police-phone guards are skipped when the channel is WEB. The interceptor is a no-op for
 * handler methods that carry no guard annotations.
 */
public class GuardInterceptor implements HandlerInterceptor {

  private final IncidentAccessPort incidentAccessPort;
  private final PolicePhoneValidationPort policePhoneValidationPort;

  public GuardInterceptor(
      IncidentAccessPort incidentAccessPort, PolicePhoneValidationPort policePhoneValidationPort) {
    this.incidentAccessPort = incidentAccessPort;
    this.policePhoneValidationPort = policePhoneValidationPort;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }

    var requireChannel = method.getMethodAnnotation(RequireChannel.class);
    var requireRole = method.getMethodAnnotation(RequireRole.class);
    var requireIncidentAccess = method.getMethodAnnotation(RequireIncidentAccess.class);
    var requirePolicePhone = method.getMethodAnnotation(RequirePolicePhone.class);
    var requireRegistered = method.getMethodAnnotation(RequirePolicePhoneRegistered.class);
    var requireAssigned = method.getMethodAnnotation(RequirePolicePhoneAssigned.class);
    var requireDevice = method.getMethodAnnotation(RequireDevice.class);
    var requireDeviceRegistered = method.getMethodAnnotation(RequireDeviceRegistered.class);
    var requireDeviceAssigned = method.getMethodAnnotation(RequireDeviceAssigned.class);

    boolean hasAnyGuard =
        requireChannel != null
            || requireRole != null
            || requireIncidentAccess != null
            || requirePolicePhone != null
            || requireRegistered != null
            || requireAssigned != null
            || requireDevice != null
            || requireDeviceRegistered != null
            || requireDeviceAssigned != null;

    if (!hasAnyGuard) {
      return true;
    }

    var auth = resolveAuthentication();

    // 1. @RequireChannel — checked first so channel errors take precedence
    if (requireChannel != null) {
      enforceChannel(auth, requireChannel.value());
    }

    // 2. @RequireRole — checked after channel
    if (requireRole != null) {
      enforceRole(auth);
    }

    // 3. @RequireIncidentAccess
    if (requireIncidentAccess != null) {
      incidentAccessPort.checkAccess(auth);
    }

    // 4. Police-phone guards — skip entirely for WEB channel
    if (auth.getChannel() != Channel.WEB) {
      if (requirePolicePhone != null || requireDevice != null) {
        enforcePolicePhonePresent(auth, requireDevice != null);
      }

      if (requireRegistered != null && auth.getPolicePhoneId() != null) {
        policePhoneValidationPort.checkRegistered(parsePolicePhoneId(auth, false));
      }
      if (requireDeviceRegistered != null && auth.getPolicePhoneId() != null) {
        try {
          policePhoneValidationPort.checkRegistered(parsePolicePhoneId(auth, true));
        } catch (PolicePhoneNotRegisteredException ex) {
          throw new DeviceNotRegisteredException();
        }
      }

      if (requireAssigned != null && auth.getPolicePhoneId() != null) {
        policePhoneValidationPort.checkAssigned(parsePolicePhoneId(auth, false));
      }
      if (requireDeviceAssigned != null && auth.getPolicePhoneId() != null) {
        try {
          policePhoneValidationPort.checkAssigned(parsePolicePhoneId(auth, true));
        } catch (PolicePhoneNotAssignedException ex) {
          throw new DeviceNotAssignedException();
        }
      }
    }

    return true;
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private SuriMapAuthentication resolveAuthentication() {
    var principal = SecurityContextHolder.getContext().getAuthentication();
    if (principal instanceof SuriMapAuthentication auth) {
      return auth;
    }
    throw new ChannelNotAllowedException();
  }

  private void enforceChannel(SuriMapAuthentication auth, Channel[] allowed) {
    boolean channelAllowed = Arrays.asList(allowed).contains(auth.getChannel());
    if (!channelAllowed) {
      throw new ChannelNotAllowedException();
    }
  }

  private void enforceRole(SuriMapAuthentication auth) {
    Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
    boolean hasMemberOnly =
        authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .allMatch(a -> a.equals(Role.MEMBER.name()));
    if (hasMemberOnly) {
      throw new RoleDeniedException();
    }
  }

  private void enforcePolicePhonePresent(SuriMapAuthentication auth, boolean deviceAlias) {
    if (auth.getPolicePhoneId() == null) {
      if (deviceAlias) {
        throw new DeviceRequiredException();
      }
      throw new PolicePhoneRequiredException();
    }
  }

  private UUID parsePolicePhoneId(SuriMapAuthentication auth, boolean deviceAlias) {
    try {
      return UUID.fromString(auth.getPolicePhoneId());
    } catch (IllegalArgumentException e) {
      if (deviceAlias) {
        throw new DeviceRequiredException();
      }
      throw new PolicePhoneRequiredException();
    }
  }
}
