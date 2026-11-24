package com.payflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys", indexes = {
    @Index(name = "idx_idemp_user_key", columnList = "user_id,idempotency_key"),
    @Index(name = "idx_idemp_expires", columnList = "expires_at")
})
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PROCESSING;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public enum Status { PROCESSING, COMPLETED, FAILED }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusHours(24);
        }
    }

    public IdempotencyRecord() {}

    // All getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String key) { this.idempotencyKey = key; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String fp) { this.requestFingerprint = fp; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public Integer getResponseStatusCode() { return responseStatusCode; }
    public void setResponseStatusCode(Integer code) { this.responseStatusCode = code; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String body) { this.responseBody = body; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
