package com.surimap.config;

import com.surimap.account.security.OidcBearerAuthenticationFilter;
import com.surimap.account.security.OidcIdentityAuthenticationConverter;
import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ObjectProvider<JwtDecoder> keycloakJwtDecoder,
      ObjectProvider<OidcIdentityAuthenticationConverter> oidcIdentityAuthenticationConverter)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/api/health",
                        "/error",
                        "/actuator/health",
                        "/actuator/prometheus",
                        "/api/internal/mock-112/events",
                        "/mock-upload/**")
                    .permitAll()
                    .anyRequest()
                    .access(SecurityConfig::hasSuriMapAuthentication))
        .httpBasic(Customizer.withDefaults());

    JwtDecoder jwtDecoder = keycloakJwtDecoder.getIfAvailable();
    OidcIdentityAuthenticationConverter converter =
        oidcIdentityAuthenticationConverter.getIfAvailable();
    if (jwtDecoder != null && converter != null) {
      http.addFilterBefore(
          new OidcBearerAuthenticationFilter(jwtDecoder, converter),
          UsernamePasswordAuthenticationFilter.class);
    }

    return http.build();
  }

  @Bean
  @ConditionalOnExpression(
      "T(org.springframework.util.StringUtils).hasText('${surimap.auth.keycloak.issuer-uri:}')")
  JwtDecoder keycloakJwtDecoder(
      @Value("${surimap.auth.keycloak.issuer-uri}") String issuerUri,
      @Value("${surimap.auth.keycloak.jwk-set-uri:}") String jwkSetUri) {
    if (jwkSetUri != null && !jwkSetUri.isBlank()) {
      NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
      decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
      return decoder;
    }
    return JwtDecoders.fromIssuerLocation(issuerUri);
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${surimap.cors.allowed-origin-patterns}") String allowedOriginPatterns) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(splitCommaSeparatedList(allowedOriginPatterns));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Client-Channel",
            "X-PolicePhone-Id",
            "X-Mock112-Signature",
            "Idempotency-Key",
            "Last-Event-ID"));
    configuration.setExposedHeaders(List.of("Location"));
    configuration.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  private static List<String> splitCommaSeparatedList(String value) {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isEmpty())
        .toList();
  }

  private static AuthorizationDecision hasSuriMapAuthentication(
      Supplier<Authentication> authentication, RequestAuthorizationContext context) {
    var current = authentication.get();
    return new AuthorizationDecision(
        current instanceof SuriMapAuthentication && current.isAuthenticated());
  }

  @Bean
  UserDetailsService userDetailsService() {
    var user = User.withUsername("dev").password("{noop}dev-password").roles("DEVELOPER").build();
    return new InMemoryUserDetailsManager(user);
  }
}
