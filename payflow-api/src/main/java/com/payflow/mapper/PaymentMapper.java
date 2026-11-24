package com.payflow.mapper;

import com.payflow.dto.request.CreatePaymentRequest;
import com.payflow.dto.response.PaymentResponse;
import com.payflow.entity.Payment;
import com.payflow.entity.User;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class PaymentMapper {

    public Payment toEntity(CreatePaymentRequest request, User user) {
        Payment payment = new Payment();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setUser(user);
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency().toUpperCase());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setDescription(request.description());
        payment.setMetadata(request.metadata());
        return payment;
    }

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getPaymentId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getDescription(),
            payment.getPaymentMethod(),
            payment.getProviderTransactionId(),
            payment.getFailureReason(),
            payment.getCreatedAt(),
            payment.getUpdatedAt()
        );
    }
}
