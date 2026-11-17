package com.trustlayer.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Keeps refresh and CSRF cookie behavior consistent across login, logout, account deletion,
 * password reset, and session revocation.
 */
@Component
public class AuthenticationCookieService {

    public static final String REFRESH_COOKIE_NAME = "trustlayer_refresh";

    private final SecurityProperties securityProperties;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthenticationCookieService(
            SecurityProperties securityProperties,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.securityProperties = securityProperties;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    public CsrfToken ensureCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        }
        return token;
    }

    public void writeRefreshCookie(HttpServletResponse response, String rawToken, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(securityProperties.cookies().secure())
                .sameSite(securityProperties.cookies().sameSite())
                .path("/api/auth")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearAuthenticationCookies(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(securityProperties.cookies().secure())
                .sameSite(securityProperties.cookies().sameSite())
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        csrfTokenRepository.saveToken(null, request, response);
    }
}
