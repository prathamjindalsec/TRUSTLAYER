package com.trustlayer.controller;

import com.trustlayer.dto.ApiResponse;
import com.trustlayer.dto.threat.ThreatResponse;
import com.trustlayer.dto.threat.ThreatVerificationRequest;
import com.trustlayer.security.AuthenticatedUser;
import com.trustlayer.service.RequestMetadata;
import com.trustlayer.service.ThreatIntelligenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately separate from community reporting. Only database-backed ADMIN users can set a
 * canonical verification status; this endpoint is not an automated truth oracle.
 */
@RestController
@RequestMapping("/api/admin/threats")
public class AdminThreatController {

    private final ThreatIntelligenceService threatIntelligenceService;

    public AdminThreatController(ThreatIntelligenceService threatIntelligenceService) {
        this.threatIntelligenceService = threatIntelligenceService;
    }

    @PatchMapping("/{threatId}/verification")
    public ResponseEntity<ApiResponse<ThreatResponse>> updateVerification(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable @Positive(message = "threatId must be positive.") Long threatId,
            @Valid @RequestBody ThreatVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Threat verification status updated by an authorized reviewer.",
                threatIntelligenceService.updateVerification(
                        currentUser.id(), threatId, request, RequestMetadata.from(servletRequest)
                )
        ));
    }
}
