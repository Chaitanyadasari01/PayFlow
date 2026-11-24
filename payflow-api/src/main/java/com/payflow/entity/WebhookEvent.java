package com.payflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events", indexes = {
    @Index(name = "idx_webhook_status", columnList = "status"),
    @Index(name = "idx_webhook_next_retry", columnList = "next_retry_at")
})
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Status { PENDING, DELIVERED, FAILED }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public WebhookEvent() {}

    // All getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public LocalDateTime getLastAttemptedAt() { return lastAttemptedAt; }
    public void setLastAttemptedAt(LocalDateTime lastAttemptedAt) { this.lastAttemptedAt = lastAttemptedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
