package com.trustlayer.service;

import com.trustlayer.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PasswordPolicy {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    public void validate(String password) {
        if (password == null || password.length() < 12) {
            throw weakPassword("Password must contain at least 12 characters.");
        }
        if (exceedsBcryptInputLimit(password)) {
            throw weakPassword("Password cannot exceed 72 UTF-8 bytes.");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            throw weakPassword("Password must contain an uppercase letter.");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            throw weakPassword("Password must contain a lowercase letter.");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            throw weakPassword("Password must contain a digit.");
        }
        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            throw weakPassword("Password must contain a symbol.");
        }
    }

    public boolean exceedsBcryptInputLimit(String password) {
        return password != null
                && password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES;
    }

    private ApiException weakPassword(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", message);
    }
}
