package com.trustlayer.controller;

import com.trustlayer.dto.ApiResponse;
import com.trustlayer.dto.extension.ExtensionAuthorizationRequest;
import com.trustlayer.dto.extension.ExtensionAuthorizationResponse;
import com.trustlayer.dto.extension.ExtensionTokenRequest;
import com.trustlayer.dto.extension.ExtensionTokenResponse;
import com.trustlayer.dto.scanner.UrlScanRequest;
import com.trustlayer.dto.scanner.UrlScanResponse;
import com.trustlayer.exception.ApiException;
import com.trustlayer.security.AuthenticatedUser;
import com.trustlayer.service.ExtensionAuthorizationService;
import com.trustlayer.service.RequestMetadata;
import com.trustlayer.service.UrlScannerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Separate extension boundary. Normal browser tokens cannot call the scoped scan route, and the
 * extension credential cannot be replayed against the rest of the user API.
 */
@RestController
@RequestMapping("/api/extension")
public class ExtensionController {

    private final ExtensionAuthorizationService extensionAuthorizationService;
    private final UrlScannerService urlScannerService;

    public ExtensionController(
            ExtensionAuthorizationService extensionAuthorizationService,
            UrlScannerService urlScannerService
    ) {
        this.extensionAuthorizationService = extensionAuthorizationService;
        this.urlScannerService = urlScannerService;
    }

    @PostMapping("/authorizations")
    public ResponseEntity<ApiResponse<ExtensionAuthorizationResponse>> authorize(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ExtensionAuthorizationRequest request,
            HttpServletRequest servletRequest
    ) {
        if (!currentUser.isBrowserAccessToken()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EXTENSION_AUTHORIZATION_FORBIDDEN",
                    "A browser session is required to authorize an extension.");
        }
        ExtensionAuthorizationResponse result = extensionAuthorizationService.authorize(
                currentUser.id(),
                currentUser.sessionId(),
                request,
                RequestMetadata.from(servletRequest)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Extension authorization granted for URL scanning.", result));
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<ExtensionTokenResponse>> exchange(
            @Valid @RequestBody ExtensionTokenRequest request,
            HttpServletRequest servletRequest
    ) {
        ExtensionTokenResponse result = extensionAuthorizationService.exchange(
                request,
                RequestMetadata.from(servletRequest)
        );
        return ResponseEntity.ok(ApiResponse.success("Extension access token issued.", result));
    }

    @PostMapping("/scan-url")
    public ResponseEntity<ApiResponse<UrlScanResponse>> scanUrl(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UrlScanRequest request,
            HttpServletRequest servletRequest
    ) {
        UrlScanResponse result = urlScannerService.scan(
                currentUser.id(),
                request,
                RequestMetadata.from(servletRequest)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Extension URL analysis completed without connecting to the destination.", result));
    }
}
