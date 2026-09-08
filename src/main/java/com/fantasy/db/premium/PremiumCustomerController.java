package com.fantasy.db.premium;

import com.fantasy.db.premium.dto.PremiumCustomerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Premium", description = "Premium access: what a user is entitled to, and grants handed out by an admin")
@RestController
@RequestMapping("/api/v1/premium")
public class PremiumCustomerController {

    private final PremiumService premiumService;

    public PremiumCustomerController(PremiumService premiumService) {
        this.premiumService = premiumService;
    }

    @Operation(summary = "Everyone with premium access right now",
            description = "Paying subscribers and users on an admin grant, newest account first.")
    @ApiResponse(responseCode = "200", description = "Customers returned")
    @GetMapping("/customers")
    public List<PremiumCustomerResponse> customers() {
        return premiumService.customers();
    }
}
