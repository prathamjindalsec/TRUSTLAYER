package com.trustlayer.controller;

import com.trustlayer.dto.ApiResponse;
import com.trustlayer.dto.PageResponse;
import com.trustlayer.dto.scanner.QrScanHistoryItemResponse;
import com.trustlayer.dto.scanner.QrScanResponse;
import com.trustlayer.security.AuthenticatedUser;
import com.trustlayer.service.QrScannerService;
import com.trustlayer.service.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Authenticated QR image scanner endpoints. The POST retains global CSRF protection because a
 * browser session also includes a refresh-token cookie.
 */
@RestController
@RequestMapping("/api/scanner/qr")
public class QrScannerController {

    private final QrScannerService qrScannerService;

    public QrScannerController(QrScannerService qrScannerService) {
        this.qrScannerService = qrScannerService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QrScanResponse>> scanQr(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestPart("image") MultipartFile image,
            HttpServletRequest servletRequest
    ) {
        QrScanResponse result = qrScannerService.scan(currentUser.id(), image, RequestMetadata.from(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("QR image decoded and analyzed without opening its destination or initiating a payment.", result));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<QrScanHistoryItemResponse>>> history(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be zero or greater.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1.")
            @Max(value = 100, message = "size must not exceed 100.") int size
    ) {
        PageResponse<QrScanHistoryItemResponse> result = qrScannerService.history(currentUser.id(), PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("QR scan history retrieved.", result));
    }

    @GetMapping("/{scanId}")
    public ResponseEntity<ApiResponse<QrScanResponse>> scanById(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable @Positive(message = "scanId must be positive.") Long scanId
    ) {
        return ResponseEntity.ok(ApiResponse.success("QR scan retrieved.", qrScannerService.findById(currentUser.id(), scanId)));
    }
}
