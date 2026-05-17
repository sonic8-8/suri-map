package com.mock112.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain mock112SecurityFilterChain(
            HttpSecurity http,
            @Value("${mock112.internal-api.token:}") String internalApiToken) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .addFilterBefore(
                        new InternalApiTokenAuthenticationFilter(internalApiToken),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/mock-112/health",
                                "/mock-112/oauth2/authorization/**",
                                "/mock-112/login/oauth2/code/**",
                                "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/mock-112/oauth2/authorization/keycloak")
                        .authorizationEndpoint(endpoint ->
                                endpoint.baseUri("/mock-112/oauth2/authorization"))
                        .redirectionEndpoint(endpoint ->
                                endpoint.baseUri("/mock-112/login/oauth2/code/*"))
                        .defaultSuccessUrl("/mock-112/", true)
                        .failureUrl("/mock-112/oauth2/authorization/keycloak"))
                .logout(logout -> logout
                        .logoutUrl("/mock-112/logout")
                        .logoutSuccessUrl("/mock-112/"));

        return http.build();
    }
}
