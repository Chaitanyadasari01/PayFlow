package com.payflow.service;

import com.payflow.dto.request.RefundRequest;
import com.payflow.dto.response.RefundResponse;
import com.payflow.entity.Payment;
import com.payflow.entity.Refund;
import com.payflow.entity.User;
import com.payflow.enums.PaymentStatus;
import com.payflow.enums.RefundStatus;
import com.payflow.exception.RefundException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.mapper.RefundMapper;
import com.payflow.provider.SimulatedPaymentProvider;
import com.payflow.provider.SimulatedPaymentProvider.ProviderResult;
import com.payflow.repository.PaymentRepository;
import com.payflow.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SimulatedPaymentProvider paymentProvider;

    @Mock
    private WebhookService webhookService;

    private RefundMapper refundMapper;
    private RefundService refundService;

    private User testUser;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        refundMapper = new RefundMapper();
        refundService = new RefundService(
                refundRepository,
                paymentRepository,
                refundMapper,
                paymentProvider,
                webhookService
        );

        testUser = new User("owner@test.com", "hash", "Owner User");
        testUser.setId(10L);

        testPayment = new Payment();
        testPayment.setId(100L);
        testPayment.setPaymentId("pay-success-123");
        testPayment.setUser(testUser);
        testPayment.setAmount(new BigDecimal("100.00"));
        testPayment.setCurrency("USD");
        testPayment.setStatus(PaymentStatus.SUCCESS);
        testPayment.setProviderTransactionId("TXN-ORIGINAL");
    }

    @Test
    @DisplayName("Should process full refund successfully and transition payment status to REFUNDED")
    void processRefund_FullRefund_Success() {
        RefundRequest request = new RefundRequest(new BigDecimal("100.00"), "Customer requested full return");

        when(paymentRepository.findByPaymentIdForUpdate("pay-success-123")).thenReturn(Optional.of(testPayment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatus(100L, RefundStatus.SUCCESS)).thenReturn(BigDecimal.ZERO);

        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.processRefund("TXN-ORIGINAL", new BigDecimal("100.00")))
                .thenReturn(new ProviderResult(ProviderResult.Status.SUCCESS, "REF-123456", null));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundResponse response = refundService.processRefund("pay-success-123", request, 10L);

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(response.providerRefundId()).isEqualTo("REF-123456");

        assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(testPayment);
        verify(webhookService).createEvent(eq("refund.succeeded"), eq(testPayment), any());
    }

    @Test
    @DisplayName("Should process partial refund and transition payment status to PARTIALLY_REFUNDED")
    void processRefund_PartialRefund_Success() {
        RefundRequest request = new RefundRequest(new BigDecimal("40.00"), "Partial return item 1");

        when(paymentRepository.findByPaymentIdForUpdate("pay-success-123")).thenReturn(Optional.of(testPayment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatus(100L, RefundStatus.SUCCESS)).thenReturn(BigDecimal.ZERO);

        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.processRefund("TXN-ORIGINAL", new BigDecimal("40.00")))
                .thenReturn(new ProviderResult(ProviderResult.Status.SUCCESS, "REF-PARTIAL", null));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundResponse response = refundService.processRefund("pay-success-123", request, 10L);

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(response.status()).isEqualTo(RefundStatus.SUCCESS);

        assertThat(testPayment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        verify(paymentRepository).save(testPayment);
    }

    @Test
    @DisplayName("Should throw RefundException when refund amount exceeds remaining refundable balance")
    void processRefund_AmountExceedsRemaining_ThrowsException() {
        RefundRequest request = new RefundRequest(new BigDecimal("70.00"), "Exceeding refund");

        when(paymentRepository.findByPaymentIdForUpdate("pay-success-123")).thenReturn(Optional.of(testPayment));
        // Already refunded 50.00 out of 100.00
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatus(100L, RefundStatus.SUCCESS))
                .thenReturn(new BigDecimal("50.00"));

        assertThatThrownBy(() -> refundService.processRefund("pay-success-123", request, 10L))
                .isInstanceOf(RefundException.class)
                .hasMessageContaining("exceeds remaining refundable balance");

        verify(paymentProvider, never()).processRefund(any(), any());
    }

    @Test
    @DisplayName("Should throw RefundException when payment status is not SUCCESS or PARTIALLY_REFUNDED")
    void processRefund_InvalidStatus_ThrowsException() {
        testPayment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByPaymentIdForUpdate("pay-success-123")).thenReturn(Optional.of(testPayment));

        RefundRequest request = new RefundRequest(new BigDecimal("50.00"), "Invalid status test");

        assertThatThrownBy(() -> refundService.processRefund("pay-success-123", request, 10L))
                .isInstanceOf(RefundException.class)
                .hasMessageContaining("Payment is not eligible for refund");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when payment does not belong to requesting user")
    void processRefund_UnauthorizedUser_ThrowsNotFound() {
        when(paymentRepository.findByPaymentIdForUpdate("pay-success-123")).thenReturn(Optional.of(testPayment));

        RefundRequest request = new RefundRequest(new BigDecimal("50.00"), "Unauthorized attempt");

        assertThatThrownBy(() -> refundService.processRefund("pay-success-123", request, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should mark refund FAILED and throw RefundException when provider fails")
    void processRefund_ProviderError_ThrowsException() {
        RefundRequest request = new RefundRequest(new BigDecimal("50.00"), "Error test");

        when(paymentRepository.findByPaymentIdForUpdate("pay-success-123")).thenReturn(Optional.of(testPayment));
        when(refundRepository.sumRefundedAmountByPaymentIdAndStatus(100L, RefundStatus.SUCCESS)).thenReturn(BigDecimal.ZERO);
        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        when(paymentProvider.processRefund("TXN-ORIGINAL", new BigDecimal("50.00")))
                .thenReturn(new ProviderResult(ProviderResult.Status.ERROR, null, "Provider refund unavailable"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> refundService.processRefund("pay-success-123", request, 10L))
                .isInstanceOf(RefundException.class)
                .hasMessageContaining("Refund processing failed");

        verify(webhookService).createEvent(eq("refund.failed"), eq(testPayment), any());
    }

    @Test
    @DisplayName("Should retrieve refunds for payment when owned by user")
    void getRefundsByPaymentId_Success() {
        when(paymentRepository.findByPaymentId("pay-success-123")).thenReturn(Optional.of(testPayment));

        Refund refund = new Refund();
        refund.setRefundId("ref-abc");
        refund.setPayment(testPayment);
        refund.setAmount(new BigDecimal("30.00"));
        refund.setStatus(RefundStatus.SUCCESS);

        when(refundRepository.findByPaymentId(100L)).thenReturn(List.of(refund));

        List<RefundResponse> responses = refundService.getRefundsByPaymentId("pay-success-123", 10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).refundId()).isEqualTo("ref-abc");
    }
}
