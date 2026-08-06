package com.fantasy.db.subscription;

import com.fantasy.db.subscription.dto.SubscriptionResponse;
import com.fantasy.db.subscription.dto.UpsertSubscriptionRequest;
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

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Subscriptions", description = "A user's billing subscription, at most one per user")
@RestController
@RequestMapping("/api/v1/users/{userId}/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Operation(summary = "Fetch a user's subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription found"),
        @ApiResponse(responseCode = "404", description = "The user has no subscription")
    })
    @GetMapping
    public SubscriptionResponse get(@PathVariable UUID userId) {
        return SubscriptionResponse.from(subscriptionService.find(userId), Instant.now());
    }

    @Operation(summary = "Create or update a user's subscription (idempotent upsert from a provider event)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription created or updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "409", description = "Concurrent create race for the same user")
    })
    @PutMapping
    public SubscriptionResponse upsert(
            @PathVariable UUID userId,
            @Valid @RequestBody UpsertSubscriptionRequest request) {
        return SubscriptionResponse.from(subscriptionService.upsert(userId, request), Instant.now());
    }
}
