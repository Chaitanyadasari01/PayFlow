package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.request.CreatePaymentRequest;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.PaymentResponse;
import com.payflow.entity.Payment;
import com.payflow.entity.User;
import com.payflow.enums.PaymentStatus;
import com.payflow.exception.PaymentProcessingException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.mapper.PaymentMapper;
import com.payflow.provider.SimulatedPaymentProvider;
import com.payflow.provider.SimulatedPaymentProvider.ProviderResult;
import com.payflow.repository.PaymentRepository;
import com.payflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentMapper paymentMapper;
    private final SimulatedPaymentProvider paymentProvider;
    private final IdempotencyService idempotencyService;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          UserRepository userRepository,
                          PaymentMapper paymentMapper,
                          SimulatedPaymentProvider paymentProvider,
                          IdempotencyService idempotencyService,
                          WebhookService webhookService,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.paymentMapper = paymentMapper;
        this.paymentProvider = paymentProvider;
        this.idempotencyService = idempotencyService;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    public PaymentResponse processPaymentWithIdempotency(CreatePaymentRequest request, Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // Direct processing without idempotency
            return executePaymentCreation(request, userId);
        }

        String fingerprint = idempotencyService.computeFingerprint(request);
        IdempotencyService.IdempotencyResult checkResult = idempotencyService.acquireOrCheck(idempotencyKey, userId, fingerprint);

        if (checkResult.action() == IdempotencyService.IdempotencyAction.RETURN_CACHED) {
            log.info("Returning cached response for idempotency key: {}", idempotencyKey);
            try {
                return objectMapper.readValue(checkResult.cachedRecord().getResponseBody(), PaymentResponse.class);
            } catch (Exception e) {
                log.error("Failed to deserialize cached response, re-fetching from DB", e);
                Payment payment = paymentRepository.findByPaymentId(checkResult.cachedRecord().getPayment().getPaymentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
                return paymentMapper.toResponse(payment);
            }
        }

        try {
            PaymentResponse response = executePaymentCreation(request, userId);
            idempotencyService.markCompleted(idempotencyKey, response, 201);
            return response;
        } catch (Exception ex) {
            idempotencyService.markFailed(idempotencyKey);
            throw ex;
        }
    }

    @Transactional
    public PaymentResponse executePaymentCreation(CreatePaymentRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // 1. Create and persist initial PENDING payment
        Payment payment = paymentMapper.toEntity(request, user);
        payment.setStatus(PaymentStatus.PROCESSING);
        payment = paymentRepository.saveAndFlush(payment);

        // 2. Call simulated external payment provider
        ProviderResult result = paymentProvider.processPayment(
                payment.getAmount(), payment.getCurrency(), payment.getPaymentMethod());

        // 3. Update payment status according to provider response
        if (result.status() == ProviderResult.Status.SUCCESS) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setProviderTransactionId(result.transactionId());
            payment = paymentRepository.save(payment);
            webhookService.createEvent("payment.succeeded", payment);
        } else if (result.status() == ProviderResult.Status.DECLINED) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProviderTransactionId(result.transactionId());
            payment.setFailureReason(result.failureReason());
            payment = paymentRepository.save(payment);
            webhookService.createEvent("payment.failed", payment);
        } else { // ProviderResult.Status.ERROR
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
            payment = paymentRepository.save(payment);
            webhookService.createEvent("payment.failed", payment);
            throw new PaymentProcessingException("Payment gateway error: " + result.failureReason());
        }

        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByPaymentId(String paymentId, Long userId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        if (!payment.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Payment not found with id: " + paymentId);
        }

        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> listPayments(Long userId, PaymentStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Payment> paymentPage;

        if (status != null) {
            paymentPage = paymentRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        } else {
            paymentPage = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return new PagedResponse<>(
                paymentPage.getContent().stream().map(paymentMapper::toResponse).toList(),
                paymentPage.getNumber(),
                paymentPage.getSize(),
                paymentPage.getTotalElements(),
                paymentPage.getTotalPages(),
                paymentPage.isLast()
        );
    }
}
