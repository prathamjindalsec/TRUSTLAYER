package com.trustlayer.dto.auth;

import com.trustlayer.dto.user.UserResponse;

public record RegistrationResponse(
        UserResponse user,
        String developmentActionToken
) {
}
