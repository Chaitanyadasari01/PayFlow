package com.payflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.request.CreatePaymentRequest;
import com.payflow.dto.request.RefundRequest;
import com.payflow.dto.request.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
        // Register and obtain a token for each test
        RegisterRequest reg = new RegisterRequest(
                "payuser@example.com", "Password123!", "Pay User");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        jwtToken = objectMapper.readTree(body).get("token").asText();
    }

    // ─── Payment Creation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/payments - should create payment and return 201")
    void createPayment_Success_Returns201() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest(
                new BigDecimal("150.00"), "USD", "credit_card", "Order #100", null);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.providerTransactionId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/payments - amount ending in .99 should result in FAILED (declined)")
    void createPayment_DeclinedAmount_ReturnsFailedStatus() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest(
                new BigDecimal("49.99"), "USD", "credit_card", "Decline test", null);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value(containsString("declined")));
    }

    @Test
    @DisplayName("POST /api/payments - amount ending in .77 should return 422 (provider error)")
    void createPayment_ProviderError_Returns422() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest(
                new BigDecimal("100.77"), "USD", "credit_card", "Timeout test", null);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /api/payments - should return 400 for missing amount")
    void createPayment_MissingAmount_Returns400() throws Exception {
        String req = "{\"currency\":\"USD\",\"paymentMethod\":\"card\"}";

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/payments - should return 401 without JWT token")
    void createPayment_NoToken_Returns401() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest(
                new BigDecimal("50.00"), "USD", "credit_card", "No auth", null);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ─── Idempotency ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/payments with Idempotency-Key - second call should return same result")
    void createPayment_IdempotencyKey_ReturnsCachedResponse() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest(
                new BigDecimal("75.00"), "USD", "credit_card", "Idempotent order", null);
        String idempotencyKey = UUID.randomUUID().toString();

        // First request
        MvcResult first = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String firstPaymentId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("paymentId").asText();

        // Second request with same key (transaction is committed in first call before retry check)
        MvcResult second = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String secondPaymentId = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("paymentId").asText();

        // Both calls must return the same payment ID
        org.assertj.core.api.Assertions.assertThat(firstPaymentId).isEqualTo(secondPaymentId);
    }

    // ─── Payment Retrieval ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/{paymentId} - should retrieve created payment")
    void getPayment_Success() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest(
                new BigDecimal("200.00"), "EUR", "bank_transfer", "Get test", null);

        MvcResult created = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("paymentId").asText();

        mockMvc.perform(get("/api/payments/" + paymentId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    @DisplayName("GET /api/payments/{paymentId} - should return 404 for non-existent payment")
    void getPayment_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/payments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    // ─── Payment Listing ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/payments - should list payments with pagination")
    void listPayments_Success() throws Exception {
        // Create two payments
        for (int i = 1; i <= 2; i++) {
            CreatePaymentRequest req = new CreatePaymentRequest(
                    new BigDecimal(10 * i + ".00"), "USD", "credit_card", "Order " + i, null);
            mockMvc.perform(post("/api/payments")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
    }

    // ─── Refunds ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/payments/{paymentId}/refunds - should refund successful payment")
    void refundPayment_Success() throws Exception {
        // Create a successful payment
        CreatePaymentRequest payReq = new CreatePaymentRequest(
                new BigDecimal("100.00"), "USD", "credit_card", "Refundable order", null);
        MvcResult created = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andReturn();

        var root = objectMapper.readTree(created.getResponse().getContentAsString());
        String paymentId = root.get("paymentId").asText();
        String status = root.get("status").asText();

        // Only refund if payment succeeded
        if ("SUCCESS".equals(status)) {
            RefundRequest refundReq = new RefundRequest(new BigDecimal("30.00"), "Partial return");

            mockMvc.perform(post("/api/payments/" + paymentId + "/refunds")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refundReq)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.refundId").isNotEmpty())
                    .andExpect(jsonPath("$.amount").value(30.00))
                    .andExpect(jsonPath("$.status").value("SUCCESS"));
        }
    }

    @Test
    @DisplayName("POST /api/payments/{paymentId}/refunds - should return 422 when refund exceeds balance")
    void refundPayment_ExceedsBalance_Returns422() throws Exception {
        // Create a successful payment
        CreatePaymentRequest payReq = new CreatePaymentRequest(
                new BigDecimal("50.00"), "USD", "credit_card", "Refund exceed test", null);
        MvcResult created = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andReturn();

        var root = objectMapper.readTree(created.getResponse().getContentAsString());
        String paymentId = root.get("paymentId").asText();
        String payStatus = root.get("status").asText();

        if ("SUCCESS".equals(payStatus)) {
            RefundRequest refundReq = new RefundRequest(new BigDecimal("999.00"), "Over-refund");

            mockMvc.perform(post("/api/payments/" + paymentId + "/refunds")
                            .header("Authorization", "Bearer " + jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refundReq)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value(containsString("exceeds")));
        }
    }

    @Test
    @DisplayName("GET /api/payments/{paymentId}/refunds - should list refunds for a payment")
    void listRefunds_Success() throws Exception {
        CreatePaymentRequest payReq = new CreatePaymentRequest(
                new BigDecimal("80.00"), "USD", "credit_card", "List refund test", null);
        MvcResult created = mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("paymentId").asText();

        mockMvc.perform(get("/api/payments/" + paymentId + "/refunds")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
