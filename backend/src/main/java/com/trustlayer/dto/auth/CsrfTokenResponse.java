package com.trustlayer.dto.auth;

public record CsrfTokenResponse(
        String token,
        String headerName
) {
}
