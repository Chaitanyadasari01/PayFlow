package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.request.CreatePaymentRequest;
import com.payflow.entity.IdempotencyRecord;
import com.payflow.exception.IdempotencyConflictException;
import com.payflow.repository.IdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    private ObjectMapper objectMapper;
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        idempotencyService = new IdempotencyService(idempotencyRepository, objectMapper);
    }

    @Test
    @DisplayName("Should generate deterministic SHA-256 fingerprint for payment request")
    void computeFingerprint_Deterministic() {
        CreatePaymentRequest req1 = new CreatePaymentRequest(
                new BigDecimal("100.00"), "USD", "card", "desc", null);
        CreatePaymentRequest req2 = new CreatePaymentRequest(
                new BigDecimal("100.00"), "USD", "card", "desc", null);
        CreatePaymentRequest req3 = new CreatePaymentRequest(
                new BigDecimal("100.01"), "USD", "card", "desc", null);

        String fp1 = idempotencyService.computeFingerprint(req1);
        String fp2 = idempotencyService.computeFingerprint(req2);
        String fp3 = idempotencyService.computeFingerprint(req3);

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).isNotEqualTo(fp3);
    }

    @Test
    @DisplayName("Should acquire lock for brand new idempotency key")
    void acquireOrCheck_NewKey_Proceeds() {
        when(idempotencyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(idempotencyRepository.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        IdempotencyService.IdempotencyResult result = idempotencyService.acquireOrCheck("key-1", 10L, "fp-123");

        assertThat(result.action()).isEqualTo(IdempotencyService.IdempotencyAction.PROCEED);
        verify(idempotencyRepository).saveAndFlush(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Should return cached record when key is COMPLETED and fingerprint matches")
    void acquireOrCheck_CompletedKey_ReturnsCached() {
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setIdempotencyKey("key-completed");
        existing.setUserId(10L);
        existing.setRequestFingerprint("fp-123");
        existing.setStatus(IdempotencyRecord.Status.COMPLETED);
        existing.setResponseBody("{\"paymentId\":\"pay-1\"}");

        when(idempotencyRepository.findByIdempotencyKey("key-completed")).thenReturn(Optional.of(existing));

        IdempotencyService.IdempotencyResult result = idempotencyService.acquireOrCheck("key-completed", 10L, "fp-123");

        assertThat(result.action()).isEqualTo(IdempotencyService.IdempotencyAction.RETURN_CACHED);
        assertThat(result.cachedRecord()).isEqualTo(existing);
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when key was used with different payload")
    void acquireOrCheck_FingerprintMismatch_ThrowsConflict() {
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setIdempotencyKey("key-conflict");
        existing.setUserId(10L);
        existing.setRequestFingerprint("fp-ORIGINAL");
        existing.setStatus(IdempotencyRecord.Status.COMPLETED);

        when(idempotencyRepository.findByIdempotencyKey("key-conflict")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.acquireOrCheck("key-conflict", 10L, "fp-DIFFERENT"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different request payload");
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when key belongs to another user")
    void acquireOrCheck_DifferentUser_ThrowsConflict() {
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setIdempotencyKey("key-user");
        existing.setUserId(99L);
        existing.setRequestFingerprint("fp-123");

        when(idempotencyRepository.findByIdempotencyKey("key-user")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.acquireOrCheck("key-user", 10L, "fp-123"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("belongs to another user");
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when request is actively PROCESSING")
    void acquireOrCheck_CurrentlyProcessing_ThrowsConflict() {
        IdempotencyRecord existing = new IdempotencyRecord();
        existing.setIdempotencyKey("key-proc");
        existing.setUserId(10L);
        existing.setRequestFingerprint("fp-123");
        existing.setStatus(IdempotencyRecord.Status.PROCESSING);
        // Created just 10 seconds ago (not stale)
        try {
            var field = IdempotencyRecord.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(existing, LocalDateTime.now().minusSeconds(10));
        } catch (Exception ignored) {}

        when(idempotencyRepository.findByIdempotencyKey("key-proc")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.acquireOrCheck("key-proc", 10L, "fp-123"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("currently being processed");
    }
}
