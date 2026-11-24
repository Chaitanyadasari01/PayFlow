package com.payflow.repository;

import com.payflow.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    List<WebhookEvent> findByPaymentId(Long paymentId);

    Optional<WebhookEvent> findByEventId(String eventId);

    @Query("SELECT w FROM WebhookEvent w WHERE w.status = 'PENDING' AND w.nextRetryAt <= :now AND w.attempts < w.maxAttempts ORDER BY w.nextRetryAt ASC")
    List<WebhookEvent> findPendingEventsForRetry(@Param("now") LocalDateTime now);
}
