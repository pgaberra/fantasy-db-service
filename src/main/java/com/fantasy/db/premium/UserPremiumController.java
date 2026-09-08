package com.fantasy.db.premium;

import com.fantasy.db.premium.dto.GrantPremiumRequest;
import com.fantasy.db.premium.dto.PremiumEntitlementResponse;
import com.fantasy.db.premium.dto.PremiumGrantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Premium", description = "Premium access: what a user is entitled to, and grants handed out by an admin")
@RestController
@RequestMapping("/api/v1/users/{userId}/premium")
public class UserPremiumController {

    private final PremiumService premiumService;

    public UserPremiumController(PremiumService premiumService) {
        this.premiumService = premiumService;
    }

    @Operation(summary = "What premium access a user has right now",
            description = "Resolves the user's subscription and any admin grant into one answer. "
                    + "Always 200: a user with neither reads as no premium.")
    @ApiResponse(responseCode = "200", description = "Entitlement returned")
    @GetMapping
    public PremiumEntitlementResponse entitlement(@PathVariable UUID userId) {
        return premiumService.entitlement(userId);
    }

    @Operation(summary = "Every premium grant a user has been given, newest first")
    @ApiResponse(responseCode = "200", description = "Grants returned")
    @GetMapping("/grants")
    public List<PremiumGrantResponse> grants(@PathVariable UUID userId) {
        Instant now = Instant.now();
        return premiumService.grants(userId).stream()
                .map(grant -> PremiumGrantResponse.from(grant, now))
                .toList();
    }

    @Operation(summary = "Give a user premium until a date, without a payment")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Grant created"),
        @ApiResponse(responseCode = "400", description = "Validation failed (missing or past expiry)"),
        @ApiResponse(responseCode = "404", description = "No user with that id")
    })
    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public PremiumGrantResponse grant(@PathVariable UUID userId, @Valid @RequestBody GrantPremiumRequest request) {
        return PremiumGrantResponse.from(premiumService.grant(userId, request), Instant.now());
    }

    @Operation(summary = "End every grant the user still has",
            description = "Marks them revoked rather than deleting them, so the audit trail survives. "
                    + "A user with no active grant is left as they are.")
    @ApiResponse(responseCode = "204", description = "Any active grants were revoked")
    @DeleteMapping("/grants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeGrants(@PathVariable UUID userId) {
        premiumService.revokeActiveGrants(userId);
    }
}
