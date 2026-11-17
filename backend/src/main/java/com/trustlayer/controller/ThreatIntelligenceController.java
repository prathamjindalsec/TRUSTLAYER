package com.trustlayer.controller;

import com.trustlayer.dto.ApiResponse;
import com.trustlayer.dto.PageResponse;
import com.trustlayer.dto.threat.ThreatReportRequest;
import com.trustlayer.dto.threat.ThreatReportResponse;
import com.trustlayer.dto.threat.ThreatResponse;
import com.trustlayer.dto.threat.ThreatStatisticsResponse;
import com.trustlayer.entity.enums.ThreatCategory;
import com.trustlayer.entity.enums.ThreatType;
import com.trustlayer.entity.enums.ThreatVerificationStatus;
import com.trustlayer.security.AuthenticatedUser;
import com.trustlayer.service.RequestMetadata;
import com.trustlayer.service.ThreatIntelligenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authenticated community intelligence API. Results distinguish verified threats from reports. */
@RestController
@Validated
@RequestMapping("/api/threats")
public class ThreatIntelligenceController {

    private final ThreatIntelligenceService threatIntelligenceService;

    public ThreatIntelligenceController(ThreatIntelligenceService threatIntelligenceService) {
        this.threatIntelligenceService = threatIntelligenceService;
    }

    @PostMapping("/report")
    public ResponseEntity<ApiResponse<ThreatReportResponse>> report(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ThreatReportRequest request,
            HttpServletRequest servletRequest
    ) {
        ThreatReportResponse result = threatIntelligenceService.report(
                currentUser.id(), request, RequestMetadata.from(servletRequest)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Community report received. It remains unverified unless an authorized reviewer verifies the canonical threat.",
                result
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ThreatResponse>>> search(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) ThreatType type,
            @RequestParam(required = false) ThreatCategory category,
            @RequestParam(required = false) ThreatVerificationStatus verificationStatus,
            @RequestParam(required = false) @Size(max = 2048, message = "query must not exceed 2048 characters.") String query,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be zero or greater.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1.")
            @Max(value = 100, message = "size must not exceed 100.") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Threat intelligence results retrieved.",
                threatIntelligenceService.search(currentUser.id(), type, category, verificationStatus, query,
                        PageRequest.of(page, size))));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ThreatStatisticsResponse>> statistics(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.success("Community threat statistics retrieved.",
                threatIntelligenceService.statistics(currentUser.id())));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<ApiResponse<List<ThreatResponse>>> campaigns(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "3") @Min(value = 2, message = "minReports must be at least 2.")
            @Max(value = 1000, message = "minReports must not exceed 1000.") int minReports
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Repeated-report candidates retrieved. They are not confirmed campaigns.",
                threatIntelligenceService.repeatedCampaignCandidates(currentUser.id(), minReports)
        ));
    }

    @GetMapping("/{threatId}")
    public ResponseEntity<ApiResponse<ThreatResponse>> byId(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable @Positive(message = "threatId must be positive.") Long threatId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Threat intelligence record retrieved.",
                threatIntelligenceService.findById(currentUser.id(), threatId)));
    }
}
