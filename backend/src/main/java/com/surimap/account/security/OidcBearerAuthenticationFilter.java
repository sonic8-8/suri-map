package com.surimap.account.security;

import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

public class OidcBearerAuthenticationFilter extends OncePerRequestFilter {

  private final JwtDecoder jwtDecoder;
  private final OidcIdentityAuthenticationConverter authenticationConverter;
  private final RequestAttributeSecurityContextRepository securityContextRepository =
      new RequestAttributeSecurityContextRepository();

  public OidcBearerAuthenticationFilter(
      JwtDecoder jwtDecoder, OidcIdentityAuthenticationConverter authenticationConverter) {
    this.jwtDecoder = jwtDecoder;
    this.authenticationConverter = authenticationConverter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      extractBearerToken(request.getHeader("Authorization"))
          .filter(OidcBearerAuthenticationFilter::hasThreeTokenSegments)
          .flatMap(
              accessToken ->
                  authenticate(
                      accessToken,
                      request.getHeader("X-Client-Channel"),
                      request.getHeader("X-PolicePhone-Id")))
          .ifPresent(
              authentication -> {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                // 같은 HTTP 요청의 비동기 종료 처리에서도 인증 정보를 복원한다.
                securityContextRepository.saveContext(context, request, response);
              });
    }
    filterChain.doFilter(request, response);
  }

  private Optional<SuriMapAuthentication> authenticate(
      String accessToken, String channelHeader, String policePhoneHeader) {
    try {
      return authenticationConverter.convert(
          jwtDecoder.decode(accessToken), channelHeader, policePhoneHeader);
    } catch (JwtException exception) {
      return Optional.empty();
    }
  }

  private static Optional<String> extractBearerToken(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return Optional.empty();
    }
    String value = authorization.trim();
    if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
      return Optional.of(value.substring("Bearer ".length()).trim());
    }
    return Optional.empty();
  }

  private static boolean hasThreeTokenSegments(String accessToken) {
    return accessToken.chars().filter(character -> character == '.').count() == 2;
  }
}
