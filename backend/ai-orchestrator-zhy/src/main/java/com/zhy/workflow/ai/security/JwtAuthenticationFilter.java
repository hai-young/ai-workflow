package com.zhy.workflow.ai.security;

import com.zhy.workflow.ai.entity.User;
import com.zhy.workflow.ai.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT authentication filter that extracts and validates JWT tokens from requests.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        log.info("JWT Filter: path={}, method={}, Authorization header present={}",
                request.getRequestURI(), request.getMethod(), authHeader != null);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter: 无有效 Bearer token, path={}, method={}", request.getRequestURI(), request.getMethod());
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String username;

        try {
            username = jwtUtil.extractUsername(token);
            log.info("JWT Filter: token extracted, username={}", username);
        } catch (Exception e) {
            log.warn("JWT Filter: token extraction failed: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = userRepository.findByUsername(username).orElse(null);

            if (user != null && jwtUtil.validateToken(token, username) && user.isEnabled()) {
                String role = jwtUtil.extractRole(token);
                if (role == null || role.isEmpty()) {
                    role = "ROLE_USER";
                }
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(role))
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("JWT Filter: authentication set for user={}, role={}", username, role);
            } else {
                log.debug("JWT Filter: token validation failed: user={}, valid={}, enabled={}",
                        user != null ? user.getUsername() : "null",
                        user != null && jwtUtil.validateToken(token, username),
                        user != null && user.isEnabled());
            }
        }

        filterChain.doFilter(request, response);
    }
}
