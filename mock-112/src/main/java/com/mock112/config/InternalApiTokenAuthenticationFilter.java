package com.mock112.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

final class InternalApiTokenAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Internal-Service-Token";

    private final String token;

    InternalApiTokenAuthenticationFilter(String token) {
        this.token = token;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isMock112Api(request) && StringUtils.hasText(token)) {
            String provided = request.getHeader(HEADER_NAME);
            if (token.equals(provided)) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "suri-map-backend",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isMock112Api(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.equals("/mock-112/incidents") || path.startsWith("/mock-112/incidents/");
    }
}
