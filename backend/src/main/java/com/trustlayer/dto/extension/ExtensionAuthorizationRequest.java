package com.trustlayer.dto.extension;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExtensionAuthorizationRequest(
        @NotBlank @Size(max = 100) String clientId,
        @NotBlank @Size(max = 1024) String redirectUri,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{43,128}$", message = "codeChallenge must be a valid S256 PKCE challenge.")
        String codeChallenge,
        @NotBlank @Pattern(regexp = "S256", message = "Only the S256 PKCE method is accepted.") String codeChallengeMethod,
        @NotBlank @Size(max = 512) String state
) {
}
