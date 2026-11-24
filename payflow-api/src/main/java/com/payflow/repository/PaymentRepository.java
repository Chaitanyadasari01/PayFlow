package com.payflow.repository;

import com.payflow.entity.Payment;
import com.payflow.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentId(String paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :paymentId")
    Optional<Payment> findByPaymentIdForUpdate(@Param("paymentId") String paymentId);

    Page<Payment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Payment> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PaymentStatus status, Pageable pageable);
}
