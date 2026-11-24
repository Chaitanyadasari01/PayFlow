package com.payflow.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreatePaymentRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Digits(integer = 15, fraction = 4, message = "Amount format is invalid")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    String currency,

    @NotBlank(message = "Payment method is required")
    @Size(max = 50)
    String paymentMethod,

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description,

    String metadata
) {}
