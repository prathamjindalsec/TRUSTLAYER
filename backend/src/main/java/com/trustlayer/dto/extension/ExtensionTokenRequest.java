package com.trustlayer.dto.extension;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExtensionTokenRequest(
        @NotBlank @Size(max = 100) String clientId,
        @NotBlank @Size(max = 1024) String redirectUri,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{43,128}$", message = "code must be a valid authorization code.") String code,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._~-]{43,128}$", message = "codeVerifier must be a valid PKCE verifier.")
        String codeVerifier
) {
}
