package com.payflow.controller;

import com.payflow.dto.response.ApiErrorResponse;
import com.payflow.dto.response.WebhookEventResponse;
import com.payflow.entity.WebhookEvent;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.security.CustomUserPrincipal;
import com.payflow.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks", description = "Endpoints for inspecting webhook delivery events and triggering retries")
@SecurityRequirement(name = "Bearer Authentication")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Operation(summary = "Get webhook events for a payment", description = "Retrieves all webhook events dispatched for the specified payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook events retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = WebhookEventResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<List<WebhookEventResponse>> getEventsForPayment(
            @PathVariable String paymentId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<WebhookEventResponse> events = webhookService.getEventResponsesByPaymentId(paymentId, principal.getUserId());
        return ResponseEntity.ok(events);
    }

    @Operation(summary = "Get webhook event by event ID", description = "Retrieves a single webhook event by its event UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook event retrieved",
                    content = @Content(schema = @Schema(implementation = WebhookEventResponse.class))),
            @ApiResponse(responseCode = "404", description = "Event not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/events/{eventId}")
    public ResponseEntity<WebhookEventResponse> getEventById(
            @PathVariable String eventId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        WebhookEvent event = webhookService.getEventByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook event not found with id: " + eventId));

        if (event.getPayment() != null && !event.getPayment().getUser().getId().equals(principal.getUserId())) {
            throw new ResourceNotFoundException("Webhook event not found with id: " + eventId);
        }

        return ResponseEntity.ok(webhookService.toResponse(event));
    }

    @Operation(summary = "Trigger webhook retry cycle", description = "Manually triggers retry processing for all pending webhooks due for retry")
    @ApiResponse(responseCode = "200", description = "Retry processing completed")
    @PostMapping("/retry")
    public ResponseEntity<Map<String, Object>> triggerRetry() {
        int processed = webhookService.processPendingRetries();
        return ResponseEntity.ok(Map.of(
                "message", "Webhook retry cycle executed",
                "processedEventsCount", processed
        ));
    }
}
