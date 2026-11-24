package com.payflow.dto.response;

import com.payflow.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
    String paymentId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String description,
    String paymentMethod,
    String providerTransactionId,
    String failureReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
