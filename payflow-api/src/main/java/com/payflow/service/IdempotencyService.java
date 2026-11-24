package com.payflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.request.CreatePaymentRequest;
import com.payflow.entity.IdempotencyRecord;
import com.payflow.exception.IdempotencyConflictException;
import com.payflow.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    public String computeFingerprint(CreatePaymentRequest request) {
        try {
            // Canonical representation: amount, currency, paymentMethod, description
            String canonicalString = String.format("%s|%s|%s|%s",
                    request.amount() != null ? request.amount().stripTrailingZeros().toPlainString() : "",
                    request.currency() != null ? request.currency().toUpperCase().trim() : "",
                    request.paymentMethod() != null ? request.paymentMethod().trim() : "",
                    request.description() != null ? request.description().trim() : ""
            );

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Attempts to acquire an idempotency lock by inserting a record with status PROCESSING.
     * If the record already exists, checks if the fingerprint matches.
     * Uses REQUIRES_NEW propagation to commit the acquisition attempt independently.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult acquireOrCheck(String key, Long userId, String fingerprint) {
        Optional<IdempotencyRecord> existingOpt = idempotencyRepository.findByIdempotencyKey(key);

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            // 1. Verify user ownership
            if (!existing.getUserId().equals(userId)) {
                throw new IdempotencyConflictException("Idempotency key belongs to another user");
            }

            // 2. Verify payload fingerprint
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(
                        "Idempotency key '" + key + "' was previously used with a different request payload.");
            }

            // 3. Check status
            if (existing.getStatus() == IdempotencyRecord.Status.PROCESSING) {
                // Check if stale (older than 2 minutes)
                if (existing.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(2))) {
                    log.warn("Stale idempotency record found for key {}, allowing retry", key);
                    existing.setStatus(IdempotencyRecord.Status.PROCESSING);
                    idempotencyRepository.save(existing);
                    return new IdempotencyResult(IdempotencyAction.PROCEED, null);
                }
                throw new IdempotencyConflictException(
                        "A request with idempotency key '" + key + "' is currently being processed. Please retry shortly.");
            }

            if (existing.getStatus() == IdempotencyRecord.Status.COMPLETED) {
                return new IdempotencyResult(IdempotencyAction.RETURN_CACHED, existing);
            }

            // If FAILED, allow re-try
            existing.setStatus(IdempotencyRecord.Status.PROCESSING);
            idempotencyRepository.save(existing);
            return new IdempotencyResult(IdempotencyAction.PROCEED, null);
        }

        // New key: Insert new PROCESSING record
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(key);
        record.setUserId(userId);
        record.setRequestFingerprint(fingerprint);
        record.setStatus(IdempotencyRecord.Status.PROCESSING);

        try {
            idempotencyRepository.saveAndFlush(record);
            return new IdempotencyResult(IdempotencyAction.PROCEED, null);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent insert race condition caught by UNIQUE constraint
            log.warn("Concurrent duplicate insertion for idempotency key: {}", key);
            IdempotencyRecord winner = idempotencyRepository.findByIdempotencyKey(key)
                    .orElseThrow(() -> new IdempotencyConflictException("Concurrent request conflict"));

            if (!winner.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException("Idempotency key was used with a different payload.");
            }
            if (winner.getStatus() == IdempotencyRecord.Status.COMPLETED) {
                return new IdempotencyResult(IdempotencyAction.RETURN_CACHED, winner);
            }
            throw new IdempotencyConflictException(
                    "A simultaneous request with this idempotency key is already in progress.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String key, Object responseBody, int statusCode) {
        idempotencyRepository.findByIdempotencyKey(key).ifPresent(record -> {
            try {
                record.setStatus(IdempotencyRecord.Status.COMPLETED);
                record.setResponseStatusCode(statusCode);
                record.setResponseBody(objectMapper.writeValueAsString(responseBody));
                idempotencyRepository.save(record);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize response body for idempotency record: {}", key, e);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String key) {
        idempotencyRepository.findByIdempotencyKey(key).ifPresent(record -> {
            record.setStatus(IdempotencyRecord.Status.FAILED);
            idempotencyRepository.save(record);
        });
    }

    public enum IdempotencyAction {
        PROCEED,
        RETURN_CACHED
    }

    public record IdempotencyResult(IdempotencyAction action, IdempotencyRecord cachedRecord) {}
}
