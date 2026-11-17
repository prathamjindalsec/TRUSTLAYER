package com.trustlayer.dto.user;

import com.trustlayer.entity.UserAccount;
import com.trustlayer.entity.enums.UserRole;
import com.trustlayer.entity.enums.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        UserStatus status,
        Instant emailVerifiedAt,
        Instant createdAt
) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getStatus(),
                user.getEmailVerifiedAt(),
                user.getCreatedAt()
        );
    }
}
