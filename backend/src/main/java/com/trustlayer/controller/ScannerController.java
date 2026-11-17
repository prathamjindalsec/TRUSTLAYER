package com.trustlayer.controller;

import com.trustlayer.dto.ApiResponse;
import com.trustlayer.dto.PageResponse;
import com.trustlayer.dto.scanner.UrlScanHistoryItemResponse;
import com.trustlayer.dto.scanner.UrlScanRequest;
import com.trustlayer.dto.scanner.UrlScanResponse;
import com.trustlayer.security.AuthenticatedUser;
import com.trustlayer.service.RequestMetadata;
import com.trustlayer.service.UrlScannerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated URL scanner endpoints. POST requests retain the application's global CSRF
 * protection because browser sessions also use a refresh-token cookie.
 */
@RestController
@RequestMapping("/api/scanner")
public class ScannerController {

    private final UrlScannerService urlScannerService;

    public ScannerController(UrlScannerService urlScannerService) {
        this.urlScannerService = urlScannerService;
    }

    @PostMapping("/url")
    public ResponseEntity<ApiResponse<UrlScanResponse>> scanUrl(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UrlScanRequest request,
            HttpServletRequest servletRequest
    ) {
        UrlScanResponse result = urlScannerService.scan(currentUser.id(), request, RequestMetadata.from(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("URL analysis completed without connecting to the destination.", result));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<UrlScanHistoryItemResponse>>> history(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be zero or greater.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1.")
            @Max(value = 100, message = "size must not exceed 100.") int size
    ) {
        PageResponse<UrlScanHistoryItemResponse> result = urlScannerService.history(
                currentUser.id(),
                PageRequest.of(page, size)
        );
        return ResponseEntity.ok(ApiResponse.success("URL scan history retrieved.", result));
    }

    @GetMapping("/{scanId}")
    public ResponseEntity<ApiResponse<UrlScanResponse>> scanById(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable @Positive(message = "scanId must be positive.") Long scanId
    ) {
        UrlScanResponse result = urlScannerService.findById(currentUser.id(), scanId);
        return ResponseEntity.ok(ApiResponse.success("URL scan retrieved.", result));
    }
}
