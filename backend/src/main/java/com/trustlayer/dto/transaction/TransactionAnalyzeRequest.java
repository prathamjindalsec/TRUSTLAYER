package com.trustlayer.dto.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Input for a user-created demonstration scenario, never a real payment instruction. */
public record TransactionAnalyzeRequest(
        @NotNull(message = "amount is required.")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01.")
        @Digits(integer = 15, fraction = 4, message = "amount must have at most 15 whole digits and 4 decimal places.")
        BigDecimal amount,

        @NotBlank(message = "currency is required.")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a three-letter ISO-style code.")
        String currency,

        @NotBlank(message = "beneficiaryIdentifier is required.")
        @Size(max = 255, message = "beneficiaryIdentifier must not exceed 255 characters.")
        String beneficiaryIdentifier,

        @NotNull(message = "transactionTime is required.")
        @PastOrPresent(message = "transactionTime must not be in the future.")
        OffsetDateTime transactionTime
) {
}
