package com.payflow.repository;

import com.payflow.entity.Refund;
import com.payflow.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByPaymentId(Long paymentId);

    Optional<Refund> findByRefundId(String refundId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.payment.id = :paymentId AND r.status = :status")
    BigDecimal sumRefundedAmountByPaymentIdAndStatus(@Param("paymentId") Long paymentId, @Param("status") RefundStatus status);
}
