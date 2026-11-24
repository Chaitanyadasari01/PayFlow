package com.payflow.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulated payment provider for educational purposes.
 * NEVER processes real money. Uses deterministic rules based on amount
 * to control success/failure outcomes for testing.
 *
 * Rules:
 * - Amount ending in .99 → DECLINED (simulates card decline)
 * - Amount ending in .77 → TIMEOUT/ERROR (simulates provider outage)
 * - Amount > 10000 → DECLINED (simulates limit exceeded)
 * - Everything else → SUCCESS
 */
@Component
public class SimulatedPaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentProvider.class);

    public ProviderResult processPayment(BigDecimal amount, String currency, String paymentMethod) {
        log.info("SIMULATED PROVIDER: Processing payment of {} {} via {}",
                amount, currency, paymentMethod);

        // Simulate processing delay (50-200ms)
        simulateNetworkDelay();

        // Deterministic failure scenarios based on amount
        String amountStr = amount.toPlainString();

        // Rule 1: Amount ending in .77 simulates a provider timeout/error
        if (amountStr.endsWith(".77") || amountStr.endsWith(".7700")) {
            log.warn("SIMULATED PROVIDER: Timeout/error for amount {}", amount);
            return new ProviderResult(
                ProviderResult.Status.ERROR,
                null,
                "Provider timeout: simulated network error"
            );
        }

        // Rule 2: Amount ending in .99 simulates a card decline
        if (amountStr.endsWith(".99") || amountStr.endsWith(".9900")) {
            log.info("SIMULATED PROVIDER: Declined for amount {}", amount);
            return new ProviderResult(
                ProviderResult.Status.DECLINED,
                "DECL-" + UUID.randomUUID().toString().substring(0, 8),
                "Card declined: insufficient funds (simulated)"
            );
        }

        // Rule 3: Amount > 10,000 simulates limit exceeded
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            log.info("SIMULATED PROVIDER: Limit exceeded for amount {}", amount);
            return new ProviderResult(
                ProviderResult.Status.DECLINED,
                "DECL-" + UUID.randomUUID().toString().substring(0, 8),
                "Transaction limit exceeded (simulated)"
            );
        }

        // Rule 4: Everything else succeeds
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        log.info("SIMULATED PROVIDER: Payment approved, transactionId={}", transactionId);
        return new ProviderResult(
            ProviderResult.Status.SUCCESS,
            transactionId,
            null
        );
    }

    public ProviderResult processRefund(String originalTransactionId, BigDecimal amount) {
        log.info("SIMULATED PROVIDER: Processing refund of {} for transaction {}",
                amount, originalTransactionId);

        simulateNetworkDelay();

        // Refund for amounts ending in .77 simulates error
        String amountStr = amount.toPlainString();
        if (amountStr.endsWith(".77") || amountStr.endsWith(".7700")) {
            return new ProviderResult(
                ProviderResult.Status.ERROR,
                null,
                "Refund processing error (simulated)"
            );
        }

        String refundTxnId = "REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        log.info("SIMULATED PROVIDER: Refund approved, refundId={}", refundTxnId);
        return new ProviderResult(ProviderResult.Status.SUCCESS, refundTxnId, null);
    }

    private void simulateNetworkDelay() {
        try {
            Thread.sleep(50 + (long)(Math.random() * 150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record ProviderResult(Status status, String transactionId, String failureReason) {
        public enum Status { SUCCESS, DECLINED, ERROR }
    }
}
