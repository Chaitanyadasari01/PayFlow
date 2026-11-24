package com.payflow.mapper;

import com.payflow.dto.response.RefundResponse;
import com.payflow.entity.Refund;
import org.springframework.stereotype.Component;

@Component
public class RefundMapper {

    public RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
            refund.getRefundId(),
            refund.getPayment().getPaymentId(),
            refund.getAmount(),
            refund.getReason(),
            refund.getStatus(),
            refund.getProviderRefundId(),
            refund.getCreatedAt()
        );
    }
}
