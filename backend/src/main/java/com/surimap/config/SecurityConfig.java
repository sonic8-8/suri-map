package com.surimap.config;

import com.surimap.account.security.AuthSessionAuthenticationFilter;
import com.surimap.account.service.AuthSessionService;
import com.surimap.common.auth.SuriMapAuthentication;
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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectProvider<AuthSessionService> authSessionService) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        AntPathRequestMatcher.antMatcher("/api/health"),
                        AntPathRequestMatcher.antMatcher("/api/auth/login"),
                        AntPathRequestMatcher.antMatcher("/mock-upload/**"),
                        AntPathRequestMatcher.antMatcher("/actuator/health"),
                        AntPathRequestMatcher.antMatcher("/actuator/prometheus"))
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
