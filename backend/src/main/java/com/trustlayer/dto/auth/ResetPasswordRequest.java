package com.trustlayer.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 512) String token,
        @NotBlank @Size(max = 128) String newPassword
) {
}
