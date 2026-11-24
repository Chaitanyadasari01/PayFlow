package com.payflow.controller;

import com.payflow.dto.request.CreatePaymentRequest;
import com.payflow.dto.request.RefundRequest;
import com.payflow.dto.response.ApiErrorResponse;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.PaymentResponse;
import com.payflow.dto.response.RefundResponse;
import com.payflow.enums.PaymentStatus;
import com.payflow.security.CustomUserPrincipal;
import com.payflow.service.PaymentService;
import com.payflow.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Endpoints for payment creation, retrieval, and refunds")
@SecurityRequirement(name = "Bearer Authentication")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @Operation(summary = "Create a new payment", description = "Processes a payment through simulated gateway. Supports Idempotency-Key header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment processed successfully",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payment request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflict",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Payment declined or provider processing failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(description = "Optional unique idempotency key to prevent double charging", example = "idem-abc-123")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse response = paymentService.processPaymentWithIdempotency(request, principal.getUserId(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get payment details", description = "Retrieves payment details by unique payment ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment retrieved",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable String paymentId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        PaymentResponse response = paymentService.getPaymentByPaymentId(paymentId, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List user payments", description = "Returns a paginated list of payments for the authenticated user")
    @GetMapping
    public ResponseEntity<PagedResponse<PaymentResponse>> listPayments(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<PaymentResponse> response = paymentService.listPayments(principal.getUserId(), status, page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refund a payment", description = "Processes a full or partial refund for a successful payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Refund processed successfully",
                    content = @Content(schema = @Schema(implementation = RefundResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid refund request payload",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Payment ineligible or refund amount exceeds balance",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> refundPayment(
            @PathVariable String paymentId,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody RefundRequest request) {
        RefundResponse response = refundService.processRefund(paymentId, request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List refunds for payment", description = "Retrieves all refunds initiated for a given payment")
    @GetMapping("/{paymentId}/refunds")
    public ResponseEntity<List<RefundResponse>> getRefunds(
            @PathVariable String paymentId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<RefundResponse> response = refundService.getRefundsByPaymentId(paymentId, principal.getUserId());
        return ResponseEntity.ok(response);
    }
}
