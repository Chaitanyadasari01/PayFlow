package com.payflow.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record RefundRequest(
    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be at least 0.01")
    @Digits(integer = 15, fraction = 4, message = "Amount format is invalid")
    BigDecimal amount,

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    String reason
) {}
