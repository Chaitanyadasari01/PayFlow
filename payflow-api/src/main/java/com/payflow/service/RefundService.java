package com.payflow.service;

import com.payflow.dto.request.RefundRequest;
import com.payflow.dto.response.RefundResponse;
import com.payflow.entity.Payment;
import com.payflow.entity.Refund;
import com.payflow.enums.PaymentStatus;
import com.payflow.enums.RefundStatus;
import com.payflow.exception.RefundException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.mapper.RefundMapper;
import com.payflow.provider.SimulatedPaymentProvider;
import com.payflow.provider.SimulatedPaymentProvider.ProviderResult;
import com.payflow.repository.PaymentRepository;
import com.payflow.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundMapper refundMapper;
    private final SimulatedPaymentProvider paymentProvider;
    private final WebhookService webhookService;

    public RefundService(RefundRepository refundRepository,
                         PaymentRepository paymentRepository,
                         RefundMapper refundMapper,
                         SimulatedPaymentProvider paymentProvider,
                         WebhookService webhookService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.refundMapper = refundMapper;
        this.paymentProvider = paymentProvider;
        this.webhookService = webhookService;
    }

    @Transactional
    public RefundResponse processRefund(String paymentId, RefundRequest request, Long userId) {
        // Concurrency-safe lock on the payment row prevents simultaneous over-refunding
        Payment payment = paymentRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        if (!payment.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Payment not found with id: " + paymentId);
        }

        if (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new RefundException("Payment is not eligible for refund. Current status: " + payment.getStatus());
        }

        BigDecimal alreadyRefunded = refundRepository.sumRefundedAmountByPaymentIdAndStatus(payment.getId(), RefundStatus.SUCCESS);
        if (alreadyRefunded == null) {
            alreadyRefunded = BigDecimal.ZERO;
        }

        BigDecimal remainingRefundable = payment.getAmount().subtract(alreadyRefunded);
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RefundException("Refund amount must be greater than zero");
        }

        if (request.amount().compareTo(remainingRefundable) > 0) {
            throw new RefundException(String.format(
                    "Refund amount %s exceeds remaining refundable balance %s",
                    request.amount(), remainingRefundable));
        }

        // 1. Create and persist initial PENDING refund record
        Refund refund = new Refund();
        refund.setRefundId(UUID.randomUUID().toString());
        refund.setPayment(payment);
        refund.setAmount(request.amount());
        refund.setReason(request.reason());
        refund.setStatus(RefundStatus.PENDING);
        refund = refundRepository.saveAndFlush(refund);

        // 2. Call simulated payment provider
        ProviderResult result = paymentProvider.processRefund(
                payment.getProviderTransactionId(), request.amount());

        // 3. Process provider result
        if (result.status() == ProviderResult.Status.SUCCESS) {
            refund.setStatus(RefundStatus.SUCCESS);
            refund.setProviderRefundId(result.transactionId());
            refund = refundRepository.save(refund);

            BigDecimal newTotalRefunded = alreadyRefunded.add(request.amount());
            if (newTotalRefunded.compareTo(payment.getAmount()) >= 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }
            paymentRepository.save(payment);

            webhookService.createEvent("refund.succeeded", payment, Map.of(
                    "refundId", refund.getRefundId(),
                    "refundAmount", refund.getAmount(),
                    "providerRefundId", refund.getProviderRefundId()
            ));

            log.info("Refund processed successfully: refundId={}, paymentId={}, amount={}",
                    refund.getRefundId(), paymentId, refund.getAmount());
            return refundMapper.toResponse(refund);
        } else {
            refund.setStatus(RefundStatus.FAILED);
            refundRepository.save(refund);

            webhookService.createEvent("refund.failed", payment, Map.of(
                    "refundId", refund.getRefundId(),
                    "refundAmount", refund.getAmount(),
                    "failureReason", result.failureReason() != null ? result.failureReason() : "Provider rejected refund"
            ));

            log.warn("Refund rejected by provider: refundId={}, paymentId={}, reason={}",
                    refund.getRefundId(), paymentId, result.failureReason());
            throw new RefundException("Refund processing failed: " + result.failureReason());
        }
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPaymentId(String paymentId, Long userId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        if (!payment.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Payment not found with id: " + paymentId);
        }

        List<Refund> refunds = refundRepository.findByPaymentId(payment.getId());
        return refunds.stream().map(refundMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefundById(String refundId, Long userId) {
        Refund refund = refundRepository.findByRefundId(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found with id: " + refundId));

        if (!refund.getPayment().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Refund not found with id: " + refundId);
        }

        return refundMapper.toResponse(refund);
    }
}
