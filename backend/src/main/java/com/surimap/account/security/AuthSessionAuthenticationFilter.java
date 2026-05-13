package com.surimap.account.security;

import com.surimap.account.service.AuthSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthSessionAuthenticationFilter extends OncePerRequestFilter {

  private final AuthSessionService authSessionService;

  public AuthSessionAuthenticationFilter(AuthSessionService authSessionService) {
    this.authSessionService = authSessionService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String accessToken = accessToken(request.getHeader("Authorization"));
    if (accessToken != null) {
      // TODO: Add a security filter test that proves WEB Bearer sessions can access protected APIs.
      authSessionService
          .authenticate(accessToken)
          .ifPresent(
              authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
    }
    filterChain.doFilter(request, response);
  }

  private static String accessToken(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }
    String value = authorization.trim();
    if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
      return value.substring("Bearer ".length()).trim();
    }
    return null;
  }
}
