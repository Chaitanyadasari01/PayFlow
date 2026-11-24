package com.payflow.entity;

import com.payflow.enums.RefundStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds", indexes = {
    @Index(name = "idx_refund_payment_id", columnList = "payment_id")
})
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false, unique = true, length = 36)
    private String refundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status = RefundStatus.PENDING;

    @Column(name = "provider_refund_id", length = 100)
    private String providerRefundId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Refund() {}

    // All getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus status) { this.status = status; }
    public String getProviderRefundId() { return providerRefundId; }
    public void setProviderRefundId(String id) { this.providerRefundId = id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
