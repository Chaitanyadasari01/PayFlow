package com.payflow.controller;

import com.payflow.dto.response.ApiErrorResponse;
import com.payflow.dto.response.RefundResponse;
import com.payflow.security.CustomUserPrincipal;
import com.payflow.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/refunds")
@Tag(name = "Refunds", description = "Endpoints for refund retrieval")
@SecurityRequirement(name = "Bearer Authentication")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @Operation(summary = "Get refund by ID", description = "Retrieves details of a specific refund by refund UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund retrieved",
                    content = @Content(schema = @Schema(implementation = RefundResponse.class))),
            @ApiResponse(responseCode = "404", description = "Refund not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{refundId}")
    public ResponseEntity<RefundResponse> getRefund(
            @PathVariable String refundId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        RefundResponse response = refundService.getRefundById(refundId, principal.getUserId());
        return ResponseEntity.ok(response);
    }
}
