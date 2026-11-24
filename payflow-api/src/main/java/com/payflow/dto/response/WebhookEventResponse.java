package com.payflow.dto.response;

import com.payflow.entity.WebhookEvent;
import java.time.LocalDateTime;

public record WebhookEventResponse(
    String eventId,
    String eventType,
    String paymentId,
    String payload,
    WebhookEvent.Status status,
    int attempts,
    LocalDateTime nextRetryAt,
    LocalDateTime lastAttemptedAt,
    LocalDateTime createdAt
) {}
