package com.trustlayer.controller;

import com.trustlayer.dto.ApiResponse;
import com.trustlayer.dto.auth.ActionTokenResponse;
import com.trustlayer.dto.auth.AuthSessionResponse;
import com.trustlayer.dto.auth.CsrfTokenResponse;
import com.trustlayer.dto.auth.EmailRequest;
import com.trustlayer.dto.auth.LoginRequest;
import com.trustlayer.dto.auth.RegisterRequest;
import com.trustlayer.dto.auth.RegistrationResponse;
import com.trustlayer.dto.auth.ResetPasswordRequest;
import com.trustlayer.dto.auth.TokenRequest;
import com.trustlayer.security.AuthenticationCookieService;
import com.trustlayer.security.AuthenticatedUser;
import com.trustlayer.service.AuthenticationService;
import com.trustlayer.service.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = AuthenticationCookieService.REFRESH_COOKIE_NAME;

    private final AuthenticationService authenticationService;
    private final AuthenticationCookieService authenticationCookieService;

    public AuthController(
            AuthenticationService authenticationService,
            AuthenticationCookieService authenticationCookieService
    ) {
        this.authenticationService = authenticationService;
        this.authenticationCookieService = authenticationCookieService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        CsrfToken token = authenticationCookieService.ensureCsrfToken(request, response);
        return ResponseEntity.ok(ApiResponse.success(
                "CSRF token issued.",
                new CsrfTokenResponse(token.getToken(), token.getHeaderName())
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        RegistrationResponse registration = authenticationService.register(request, RequestMetadata.from(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Registration accepted. Verify the email address before signing in.",
                        registration
                ));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody TokenRequest request,
            HttpServletRequest servletRequest
    ) {
        authenticationService.verifyEmail(request, RequestMetadata.from(servletRequest));
        return ResponseEntity.ok(ApiResponse.success("Email address verified.", null));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<ActionTokenResponse>> resendVerification(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest
    ) {
        ActionTokenResponse result = authenticationService.resendVerification(request, RequestMetadata.from(servletRequest));
        return ResponseEntity.accepted().body(ApiResponse.success(
                "If the account needs verification, a verification message has been queued.",
                result
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthenticationService.AuthResult result = authenticationService.login(request, RequestMetadata.from(servletRequest));
        authenticationCookieService.writeRefreshCookie(servletResponse, result.rawRefreshToken(), result.refreshTokenExpiresAt());
        authenticationCookieService.ensureCsrfToken(servletRequest, servletResponse);
        return ResponseEntity.ok(ApiResponse.success("Login successful.", result.response()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        AuthenticationService.AuthResult result = authenticationService.refresh(refreshToken, RequestMetadata.from(servletRequest));
        authenticationCookieService.writeRefreshCookie(servletResponse, result.rawRefreshToken(), result.refreshTokenExpiresAt());
        return ResponseEntity.ok(ApiResponse.success("Access token refreshed.", result.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        authenticationService.logout(refreshToken, RequestMetadata.from(servletRequest));
        authenticationCookieService.clearAuthenticationCookies(servletRequest, servletResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        authenticationService.logoutAll(currentUser.id(), RequestMetadata.from(servletRequest));
        authenticationCookieService.clearAuthenticationCookies(servletRequest, servletResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ActionTokenResponse>> forgotPassword(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest
    ) {
        ActionTokenResponse result = authenticationService.requestPasswordReset(request, RequestMetadata.from(servletRequest));
        return ResponseEntity.accepted().body(ApiResponse.success(
                "If an active account exists for this email, a password reset message has been queued.",
                result
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        authenticationService.resetPassword(request, RequestMetadata.from(servletRequest));
        authenticationCookieService.clearAuthenticationCookies(servletRequest, servletResponse);
        return ResponseEntity.ok(ApiResponse.success("Password reset successful. Sign in again.", null));
    }
}
