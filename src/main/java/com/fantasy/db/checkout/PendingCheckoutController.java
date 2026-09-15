package com.fantasy.db.checkout;

import com.fantasy.db.checkout.dto.PendingCheckoutResponse;
import com.fantasy.db.checkout.dto.ReplacePendingCheckoutRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Pending checkouts", description = "The one checkout a user has open with the payment provider")
@RestController
@RequestMapping("/api/v1/users/{userId}/pending-checkout")
public class PendingCheckoutController {

    private final PendingCheckoutService pendingCheckoutService;

    public PendingCheckoutController(PendingCheckoutService pendingCheckoutService) {
        this.pendingCheckoutService = pendingCheckoutService;
    }

    @Operation(summary = "Fetch the checkout a user has open")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pending checkout found"),
        @ApiResponse(responseCode = "404", description = "The user has no pending checkout")
    })
    @GetMapping
    public PendingCheckoutResponse get(@PathVariable UUID userId) {
        return PendingCheckoutResponse.from(pendingCheckoutService.find(userId));
    }

    @Operation(summary = "Store the user's open checkout, replacing the one the caller read (compare-and-set)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pending checkout stored"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "409", description = "The stored checkout changed since the caller read it; "
                + "read it again and use that one")
    })
    @PutMapping
    public PendingCheckoutResponse replace(
            @PathVariable UUID userId,
            @Valid @RequestBody ReplacePendingCheckoutRequest request) {
        return PendingCheckoutResponse.from(pendingCheckoutService.replace(userId, request));
    }
}
