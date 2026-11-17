package com.trustlayer.security;

import com.trustlayer.entity.UserAccount;
import com.trustlayer.exception.ApiException;
import com.trustlayer.repository.AuthenticationSessionRepository;
import com.trustlayer.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Duration SESSION_TOUCH_INTERVAL = Duration.ofMinutes(5);

    private static final Set<String> ANONYMOUS_AUTH_PATHS = Set.of(
            "/api/auth/csrf",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/verify-email",
            "/api/auth/resend-verification",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/extension/token"
    );

    private final JwtTokenService jwtTokenService;
    private final UserAccountRepository userAccountRepository;
    private final AuthenticationSessionRepository sessionRepository;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            UserAccountRepository userAccountRepository,
            AuthenticationSessionRepository sessionRepository,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtTokenService = jwtTokenService;
        this.userAccountRepository = userAccountRepository;
        this.sessionRepository = sessionRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return ANONYMOUS_AUTH_PATHS.contains(path)
                || path.equals("/api/health")
                || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtTokenService.JwtSubject subject = jwtTokenService.parseAuthenticationToken(authorization.substring(7));
            if (subject.tokenType() == JwtTokenService.TokenType.EXTENSION_URL_SCAN
                    && !"/api/extension/scan-url".equals(requestPath(request))) {
                throw new IllegalArgumentException("Extension token used outside its permitted endpoint.");
            }
            UserAccount user = userAccountRepository.findById(subject.userId())
                    .filter(UserAccount::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("User is unavailable."));
            boolean activeSession = sessionRepository
                    .existsBySessionIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(
                            subject.sessionId(),
                            user.getId(),
                            Instant.now()
                    );
            if (!activeSession) {
                throw new IllegalArgumentException("Session is unavailable.");
            }
            Instant now = Instant.now();
            sessionRepository.touchActiveSessionIfStale(
                    subject.sessionId(),
                    user.getId(),
                    now,
                    now.minus(SESSION_TOUCH_INTERVAL)
            );

            AuthenticatedUser principal = new AuthenticatedUser(
                    user.getId(),
                    user.getEmail(),
                    user.getRole(),
                    subject.sessionId(),
                    subject.tokenType()
            );
            List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
            if (subject.tokenType() == JwtTokenService.TokenType.EXTENSION_URL_SCAN) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_URL_SCAN"));
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ApiException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, null);
        }
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }
}
