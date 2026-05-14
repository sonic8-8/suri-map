package com.surimap.config;

import com.surimap.account.security.AuthSessionAuthenticationFilter;
import com.surimap.account.service.AuthSessionService;
import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
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
      HttpSecurity http, ObjectProvider<AuthSessionService> authSessionService) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/api/health",
                        "/api/auth/login",
                        "/error",
                        "/actuator/health",
                        "/actuator/prometheus")
                    .permitAll()
                    .anyRequest()
                    .access(SecurityConfig::hasSuriMapAuthentication))
        .httpBasic(Customizer.withDefaults());

    authSessionService.ifAvailable(
        service ->
            http.addFilterBefore(
                new AuthSessionAuthenticationFilter(service),
                UsernamePasswordAuthenticationFilter.class));

    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(
        List.of("http://localhost:*", "http://127.0.0.1:*"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "X-Client-Channel", "Idempotency-Key", "Last-Event-ID"));
    configuration.setExposedHeaders(List.of("Location"));
    configuration.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
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
