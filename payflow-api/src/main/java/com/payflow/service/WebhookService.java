package com.payflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.response.WebhookEventResponse;
import com.payflow.entity.Payment;
import com.payflow.entity.WebhookEvent;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.repository.PaymentRepository;
import com.payflow.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    // Pluggable delivery simulator for testing delivery failures and retries
    private Predicate<WebhookEvent> deliverySimulator;

    public WebhookService(WebhookEventRepository webhookEventRepository,
                          PaymentRepository paymentRepository,
                          ObjectMapper objectMapper) {
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    public void setDeliverySimulator(Predicate<WebhookEvent> deliverySimulator) {
        this.deliverySimulator = deliverySimulator;
    }

    @Transactional
    public WebhookEvent createEvent(String eventType, Payment payment) {
        return createEvent(eventType, payment, Collections.emptyMap());
    }

    @Transactional
    public WebhookEvent createEvent(String eventType, Payment payment, Map<String, Object> additionalData) {
        String eventId = UUID.randomUUID().toString();
        String payload = buildPayload(eventId, eventType, payment, additionalData);

        WebhookEvent event = new WebhookEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setPayment(payment);
        event.setPayload(payload);
        event.setStatus(WebhookEvent.Status.PENDING);
        event.setAttempts(0);
        event.setMaxAttempts(5);
        event.setNextRetryAt(LocalDateTime.now());

        WebhookEvent savedEvent = webhookEventRepository.save(event);
        log.info("Created webhook event: eventId={}, eventType={}, paymentId={}",
                eventId, eventType, payment != null ? payment.getPaymentId() : "none");

        // Attempt initial delivery
        dispatch(savedEvent);
        return savedEvent;
    }

    @Transactional
    public boolean dispatch(WebhookEvent event) {
        if (event.getStatus() != WebhookEvent.Status.PENDING) {
            log.debug("Skipping dispatch for event {} as status is {}", event.getEventId(), event.getStatus());
            return event.getStatus() == WebhookEvent.Status.DELIVERED;
        }

        event.setLastAttemptedAt(LocalDateTime.now());
        boolean success = attemptDelivery(event);

        if (success) {
            event.setStatus(WebhookEvent.Status.DELIVERED);
            event.setNextRetryAt(null);
            log.info("Webhook event delivered: eventId={}, eventType={}", event.getEventId(), event.getEventType());
        } else {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);

            if (attempts >= event.getMaxAttempts()) {
                event.setStatus(WebhookEvent.Status.FAILED);
                event.setNextRetryAt(null);
                log.warn("Webhook event failed permanently after {} attempts: eventId={}",
                        attempts, event.getEventId());
            } else {
                // Exponential backoff: 2^(attempts - 1) * 5 seconds (5s, 10s, 20s, 40s)
                long backoffSeconds = (long) Math.pow(2, attempts - 1) * 5L;
                LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(backoffSeconds);
                event.setNextRetryAt(nextRetry);
                log.info("Webhook event delivery failed (attempt {}/{}). Next retry in {}s at {}: eventId={}",
                        attempts, event.getMaxAttempts(), backoffSeconds, nextRetry, event.getEventId());
            }
        }

        webhookEventRepository.save(event);
        return success;
    }

    @Scheduled(fixedDelayString = "${payflow.webhook.retry-interval-ms:60000}")
    @Transactional
    public int processPendingRetries() {
        LocalDateTime now = LocalDateTime.now();
        List<WebhookEvent> pendingEvents = webhookEventRepository.findPendingEventsForRetry(now);

        if (pendingEvents.isEmpty()) {
            return 0;
        }

        log.info("Processing {} pending webhook retries", pendingEvents.size());
        int processed = 0;
        for (WebhookEvent event : pendingEvents) {
            if (event.getStatus() == WebhookEvent.Status.PENDING) {
                dispatch(event);
                processed++;
            }
        }
        return processed;
    }

    @Transactional(readOnly = true)
    public List<WebhookEventResponse> getEventResponsesByPaymentId(String paymentId, Long userId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        if (!payment.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Payment not found with id: " + paymentId);
        }

        List<WebhookEvent> events = webhookEventRepository.findByPaymentId(payment.getId());
        return events.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<WebhookEvent> getEventByEventId(String eventId) {
        return webhookEventRepository.findByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public List<WebhookEvent> getEventsByPaymentId(Long paymentId) {
        return webhookEventRepository.findByPaymentId(paymentId);
    }

    public WebhookEventResponse toResponse(WebhookEvent event) {
        return new WebhookEventResponse(
                event.getEventId(),
                event.getEventType(),
                event.getPayment() != null ? event.getPayment().getPaymentId() : null,
                event.getPayload(),
                event.getStatus(),
                event.getAttempts(),
                event.getNextRetryAt(),
                event.getLastAttemptedAt(),
                event.getCreatedAt()
        );
    }

    private boolean attemptDelivery(WebhookEvent event) {
        if (deliverySimulator != null) {
            return deliverySimulator.test(event);
        }

        // Deterministic simulation based on payment description or payload
        if (event.getPayload() != null && event.getPayload().contains("SIMULATE_WEBHOOK_FAILURE")) {
            return false;
        }

        if (event.getPayment() != null &&
                event.getPayment().getDescription() != null &&
                event.getPayment().getDescription().contains("SIMULATE_WEBHOOK_FAILURE")) {
            return false;
        }

        return true;
    }

    private String buildPayload(String eventId, String eventType, Payment payment, Map<String, Object> additionalData) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("eventId", eventId);
        payloadMap.put("eventType", eventType);
        payloadMap.put("timestamp", LocalDateTime.now().toString());

        Map<String, Object> data = new LinkedHashMap<>();
        if (payment != null) {
            data.put("paymentId", payment.getPaymentId());
            data.put("amount", payment.getAmount());
            data.put("currency", payment.getCurrency());
            data.put("status", payment.getStatus().name());
            data.put("paymentMethod", payment.getPaymentMethod());
            data.put("providerTransactionId", payment.getProviderTransactionId());
            if (payment.getFailureReason() != null) {
                data.put("failureReason", payment.getFailureReason());
            }
        }

        if (additionalData != null && !additionalData.isEmpty()) {
            data.putAll(additionalData);
        }

        payloadMap.put("data", data);

        try {
            return objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize webhook event payload for eventId: {}", eventId, e);
            return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\"}";
        }
    }
}
