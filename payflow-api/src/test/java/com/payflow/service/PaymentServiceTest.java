package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payflow.dto.request.CreatePaymentRequest;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.PaymentResponse;
import com.payflow.entity.IdempotencyRecord;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimulatedPaymentProvider paymentProvider;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private WebhookService webhookService;

    private PaymentMapper paymentMapper;
    private ObjectMapper objectMapper;
    private PaymentService paymentService;

    private User testUser;

    @BeforeEach
    void setUp() {
        paymentMapper = new PaymentMapper();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        paymentService = new PaymentService(
                paymentRepository,
                userRepository,
                paymentMapper,
                paymentProvider,
                idempotencyService,
                webhookService,
                objectMapper
        );

        testUser = new User("user@test.com", "hash", "Test User");
        testUser.setId(1L);
    }

    @Test
    @DisplayName("Should execute payment creation successfully when provider succeeds")
    void executePaymentCreation_Success() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                new BigDecimal("150.00"), "USD", "credit_card", "Test Payment", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        Payment pendingPayment = paymentMapper.toEntity(request, testUser);
        pendingPayment.setStatus(PaymentStatus.PROCESSING);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(pendingPayment);

        when(paymentProvider.processPayment(new BigDecimal("150.00"), "USD", "credit_card"))
                .thenReturn(new ProviderResult(ProviderResult.Status.SUCCESS, "TXN-12345678", null));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.executePaymentCreation(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.providerTransactionId()).isEqualTo("TXN-12345678");

        verify(webhookService).createEvent(eq("payment.succeeded"), any(Payment.class));
    }

    @Test
    @DisplayName("Should set status FAILED when provider declines payment")
    void executePaymentCreation_Declined() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                new BigDecimal("99.99"), "USD", "credit_card", "Decline test", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        Payment pendingPayment = paymentMapper.toEntity(request, testUser);
        pendingPayment.setStatus(PaymentStatus.PROCESSING);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(pendingPayment);

        when(paymentProvider.processPayment(new BigDecimal("99.99"), "USD", "credit_card"))
                .thenReturn(new ProviderResult(ProviderResult.Status.DECLINED, "DECL-1234", "Card declined: insufficient funds"));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.executePaymentCreation(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).contains("Card declined");

        verify(webhookService).createEvent(eq("payment.failed"), any(Payment.class));
    }

    @Test
    @DisplayName("Should throw PaymentProcessingException when provider encounters timeout error")
    void executePaymentCreation_ProviderError() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                new BigDecimal("77.77"), "USD", "credit_card", "Timeout test", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        Payment pendingPayment = paymentMapper.toEntity(request, testUser);
        pendingPayment.setStatus(PaymentStatus.PROCESSING);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(pendingPayment);

        when(paymentProvider.processPayment(new BigDecimal("77.77"), "USD", "credit_card"))
                .thenReturn(new ProviderResult(ProviderResult.Status.ERROR, null, "Provider timeout"));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> paymentService.executePaymentCreation(request, 1L))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("Payment gateway error");

        verify(webhookService).createEvent(eq("payment.failed"), any(Payment.class));
    }

    @Test
    @DisplayName("Should return cached response on idempotency replay without re-executing provider")
    void processPaymentWithIdempotency_ReturnCached() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(
                new BigDecimal("200.00"), "USD", "debit_card", "Order #1", null);

        PaymentResponse cachedResponse = new PaymentResponse(
                "pay-uuid-cached", new BigDecimal("200.00"), "USD", PaymentStatus.SUCCESS,
                "Order #1", "debit_card", "TXN-CACHED", null, LocalDateTime.now(), LocalDateTime.now()
        );

        IdempotencyRecord cachedRecord = new IdempotencyRecord();
        cachedRecord.setStatus(IdempotencyRecord.Status.COMPLETED);
        cachedRecord.setResponseBody(objectMapper.writeValueAsString(cachedResponse));

        when(idempotencyService.computeFingerprint(request)).thenReturn("fp-hash-123");
        when(idempotencyService.acquireOrCheck("key-123", 1L, "fp-hash-123"))
                .thenReturn(new IdempotencyService.IdempotencyResult(
                        IdempotencyService.IdempotencyAction.RETURN_CACHED, cachedRecord));

        PaymentResponse result = paymentService.processPaymentWithIdempotency(request, 1L, "key-123");

        assertThat(result).isNotNull();
        assertThat(result.paymentId()).isEqualTo("pay-uuid-cached");
        assertThat(result.providerTransactionId()).isEqualTo("TXN-CACHED");

        verify(paymentProvider, never()).processPayment(any(), any(), any());
    }

    @Test
    @DisplayName("Should execute and mark completed when idempotency key is new")
    void processPaymentWithIdempotency_Proceed() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                new BigDecimal("50.00"), "USD", "credit_card", "New order", null);

        when(idempotencyService.computeFingerprint(request)).thenReturn("fp-hash-456");
        when(idempotencyService.acquireOrCheck("new-key", 1L, "fp-hash-456"))
                .thenReturn(new IdempotencyService.IdempotencyResult(
                        IdempotencyService.IdempotencyAction.PROCEED, null));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        Payment pendingPayment = paymentMapper.toEntity(request, testUser);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenReturn(pendingPayment);
        when(paymentProvider.processPayment(any(), any(), any()))
                .thenReturn(new ProviderResult(ProviderResult.Status.SUCCESS, "TXN-NEW", null));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.processPaymentWithIdempotency(request, 1L, "new-key");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(idempotencyService).markCompleted(eq("new-key"), any(PaymentResponse.class), eq(201));
    }

    @Test
    @DisplayName("Should retrieve payment by ID when owned by user")
    void getPaymentByPaymentId_Success() {
        Payment payment = new Payment();
        payment.setPaymentId("pay-abc");
        payment.setUser(testUser);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentMethod("card");

        when(paymentRepository.findByPaymentId("pay-abc")).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByPaymentId("pay-abc", 1L);

        assertThat(response.paymentId()).isEqualTo("pay-abc");
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when payment belongs to different user")
    void getPaymentByPaymentId_DifferentUser_ThrowsNotFound() {
        User otherUser = new User("other@test.com", "hash", "Other User");
        otherUser.setId(99L);

        Payment payment = new Payment();
        payment.setPaymentId("pay-other");
        payment.setUser(otherUser);

        when(paymentRepository.findByPaymentId("pay-other")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getPaymentByPaymentId("pay-other", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
