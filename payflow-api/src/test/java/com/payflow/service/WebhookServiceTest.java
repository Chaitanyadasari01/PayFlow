package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.response.WebhookEventResponse;
import com.payflow.entity.Payment;
import com.payflow.entity.WebhookEvent;
import com.payflow.enums.PaymentStatus;
import com.payflow.repository.PaymentRepository;
import com.payflow.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private ObjectMapper objectMapper;
    private WebhookService webhookService;

    private Payment testPayment;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookService = new WebhookService(webhookEventRepository, paymentRepository, objectMapper);

        testPayment = new Payment();
        testPayment.setId(1L);
        testPayment.setPaymentId("pay-evt-1");
        testPayment.setAmount(new BigDecimal("100.00"));
        testPayment.setCurrency("USD");
        testPayment.setStatus(PaymentStatus.SUCCESS);
        testPayment.setPaymentMethod("card");
    }

    @Test
    @DisplayName("Should create event and immediately deliver successfully when simulation succeeds")
    void createEvent_Delivered() {
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        WebhookEvent event = webhookService.createEvent("payment.succeeded", testPayment);

        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo("payment.succeeded");
        assertThat(event.getStatus()).isEqualTo(WebhookEvent.Status.DELIVERED);
        assertThat(event.getPayload()).contains("payment.succeeded");
        assertThat(event.getNextRetryAt()).isNull();

        verify(webhookEventRepository, atLeastOnce()).save(any(WebhookEvent.class));
    }

    @Test
    @DisplayName("Should apply exponential backoff on delivery failure")
    void dispatch_DeliveryFailure_AppliesExponentialBackoff() {
        webhookService.setDeliverySimulator(evt -> false); // Always fail

        WebhookEvent event = new WebhookEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("payment.failed");
        event.setStatus(WebhookEvent.Status.PENDING);
        event.setAttempts(0);
        event.setMaxAttempts(5);
        event.setPayload("{}");

        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // First attempt failure -> 5s backoff
        boolean success1 = webhookService.dispatch(event);
        assertThat(success1).isFalse();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(WebhookEvent.Status.PENDING);
        assertThat(event.getNextRetryAt()).isAfter(LocalDateTime.now().plusSeconds(3));

        // Second attempt failure -> 10s backoff
        boolean success2 = webhookService.dispatch(event);
        assertThat(success2).isFalse();
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getNextRetryAt()).isAfter(LocalDateTime.now().plusSeconds(8));

        // Third attempt failure -> 20s backoff
        boolean success3 = webhookService.dispatch(event);
        assertThat(success3).isFalse();
        assertThat(event.getAttempts()).isEqualTo(3);
        assertThat(event.getNextRetryAt()).isAfter(LocalDateTime.now().plusSeconds(18));
    }

    @Test
    @DisplayName("Should mark event FAILED when max retry attempts reached")
    void dispatch_MaxAttemptsReached_MarksFailed() {
        webhookService.setDeliverySimulator(evt -> false);

        WebhookEvent event = new WebhookEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType("payment.failed");
        event.setStatus(WebhookEvent.Status.PENDING);
        event.setAttempts(4); // 4 prior attempts
        event.setMaxAttempts(5);
        event.setPayload("{}");

        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean success = webhookService.dispatch(event);

        assertThat(success).isFalse();
        assertThat(event.getAttempts()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(WebhookEvent.Status.FAILED);
        assertThat(event.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("Should skip delivery when event is already in DELIVERED status to avoid duplicates")
    void dispatch_AlreadyDelivered_SkipsDelivery() {
        WebhookEvent event = new WebhookEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setStatus(WebhookEvent.Status.DELIVERED);

        boolean result = webhookService.dispatch(event);

        assertThat(result).isTrue();
        verify(webhookEventRepository, never()).save(event);
    }

    @Test
    @DisplayName("Should process pending retries in scheduled batch")
    void processPendingRetries_ProcessesPendingEvents() {
        WebhookEvent event1 = new WebhookEvent();
        event1.setEventId("e1");
        event1.setStatus(WebhookEvent.Status.PENDING);
        event1.setAttempts(1);
        event1.setMaxAttempts(5);

        WebhookEvent event2 = new WebhookEvent();
        event2.setEventId("e2");
        event2.setStatus(WebhookEvent.Status.PENDING);
        event2.setAttempts(2);
        event2.setMaxAttempts(5);

        when(webhookEventRepository.findPendingEventsForRetry(any(LocalDateTime.class)))
                .thenReturn(List.of(event1, event2));
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        int processed = webhookService.processPendingRetries();

        assertThat(processed).isEqualTo(2);
        assertThat(event1.getStatus()).isEqualTo(WebhookEvent.Status.DELIVERED);
        assertThat(event2.getStatus()).isEqualTo(WebhookEvent.Status.DELIVERED);
    }
}
