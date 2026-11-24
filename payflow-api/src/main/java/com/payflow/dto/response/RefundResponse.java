package com.payflow.dto.response;

import com.payflow.enums.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
    String refundId,
    String paymentId,
    BigDecimal amount,
    String reason,
    RefundStatus status,
    String providerRefundId,
    LocalDateTime createdAt
) {}
