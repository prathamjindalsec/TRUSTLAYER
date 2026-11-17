package com.trustlayer.controller;

import com.trustlayer.dto.ApiResponse;
import com.trustlayer.dto.PageResponse;
import com.trustlayer.dto.transaction.TransactionAnalyzeRequest;
import com.trustlayer.dto.transaction.TransactionRiskHistoryItemResponse;
import com.trustlayer.dto.transaction.TransactionRiskResponse;
import com.trustlayer.security.AuthenticatedUser;
import com.trustlayer.service.RequestMetadata;
import com.trustlayer.service.TransactionRiskService;
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

/** Simulation-only transaction-risk API; it has no authority or integration to move money. */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRiskService transactionRiskService;

    public TransactionController(TransactionRiskService transactionRiskService) {
        this.transactionRiskService = transactionRiskService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<TransactionRiskResponse>> analyze(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody TransactionAnalyzeRequest request,
            HttpServletRequest servletRequest
    ) {
        TransactionRiskResponse result = transactionRiskService.analyze(
                currentUser.id(), request, RequestMetadata.from(servletRequest)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Simulated transaction risk analysis completed; no real transaction was initiated or blocked.", result
        ));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<TransactionRiskHistoryItemResponse>>> history(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be zero or greater.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1.")
            @Max(value = 100, message = "size must not exceed 100.") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Simulated transaction history retrieved.",
                transactionRiskService.history(currentUser.id(), PageRequest.of(page, size))
        ));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionRiskResponse>> byId(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable @Positive(message = "transactionId must be positive.") Long transactionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Simulated transaction analysis retrieved.", transactionRiskService.findById(currentUser.id(), transactionId)
        ));
    }
}
