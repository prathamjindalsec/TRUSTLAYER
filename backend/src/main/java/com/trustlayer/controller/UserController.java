package com.trustlayer.controller;

import com.trustlayer.dto.ApiResponse;
import com.trustlayer.dto.PageResponse;
import com.trustlayer.dto.user.DeleteAccountRequest;
import com.trustlayer.dto.user.LoginHistoryResponse;
import com.trustlayer.dto.user.SessionResponse;
import com.trustlayer.dto.user.UpdateProfileRequest;
import com.trustlayer.dto.user.UserResponse;
import com.trustlayer.security.AuthenticationCookieService;
import com.trustlayer.security.AuthenticatedUser;
import com.trustlayer.service.RequestMetadata;
import com.trustlayer.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserProfileService userProfileService;
    private final AuthenticationCookieService authenticationCookieService;

    public UserController(
            UserProfileService userProfileService,
            AuthenticationCookieService authenticationCookieService
    ) {
        this.userProfileService = userProfileService;
        this.authenticationCookieService = authenticationCookieService;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> profile(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile retrieved.",
                userProfileService.getProfile(currentUser.id())
        ));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile updated.",
                userProfileService.updateProfile(currentUser.id(), request, RequestMetadata.from(servletRequest))
        ));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<PageResponse<SessionResponse>>> activeSessions(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PageableDefault(size = 20, sort = "lastSeenAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Active sessions retrieved.",
                userProfileService.getActiveSessions(currentUser.id(), currentUser.sessionId(), pageable)
        ));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String sessionId,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        userProfileService.revokeSession(currentUser.id(), sessionId, RequestMetadata.from(servletRequest));
        if (currentUser.sessionId().equals(sessionId)) {
            authenticationCookieService.clearAuthenticationCookies(servletRequest, servletResponse);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/login-history")
    public ResponseEntity<ApiResponse<PageResponse<LoginHistoryResponse>>> loginHistory(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Login history retrieved.",
                userProfileService.getLoginHistory(currentUser.id(), pageable)
        ));
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        userProfileService.deleteAccount(currentUser.id(), request, RequestMetadata.from(servletRequest));
        authenticationCookieService.clearAuthenticationCookies(servletRequest, servletResponse);
        return ResponseEntity.noContent().build();
    }
}
