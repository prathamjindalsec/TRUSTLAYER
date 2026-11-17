package com.trustlayer.controller;

import com.trustlayer.dto.ApiResponse;
import com.trustlayer.dto.HealthStatusResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public application-level availability endpoint.
 *
 * <p>This is separate from Spring Boot Actuator's operational endpoint so the public API has a
 * stable response format from the first phase onward.</p>
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<HealthStatusResponse>> health() {
        HealthStatusResponse health = new HealthStatusResponse("UP", "trustlayer-backend");

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("TRUSTLAYER API is operational.", health));
    }
}
