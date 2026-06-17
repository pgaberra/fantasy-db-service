package com.fantasy.db.passwordreset;

import com.fantasy.db.passwordreset.dto.CreatePasswordResetTokenRequest;
import com.fantasy.db.passwordreset.dto.PasswordResetRequest;
import com.fantasy.db.passwordreset.dto.PasswordResetTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Password reset", description = "Single-use password reset token issuance and consumption")
@RestController
@RequestMapping("/api/v1/users/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Operation(summary = "Issue a single-use password reset token for the account with this email",
            description = "Returns 200 with a raw token when the email maps to a password account. "
                    + "Returns 204 when no resettable account exists (unknown email, or a Google-only "
                    + "password-less account). The caller must treat both outcomes identically so that "
                    + "account existence is never revealed to end users.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token issued"),
        @ApiResponse(responseCode = "204", description = "No resettable account; nothing issued"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank/invalid email)")
    })
    @PostMapping("/tokens")
    public ResponseEntity<PasswordResetTokenResponse> issueToken(
            @Valid @RequestBody CreatePasswordResetTokenRequest request) {
        return passwordResetService.issueToken(request.email())
                .map(issued -> ResponseEntity.ok(
                        new PasswordResetTokenResponse(issued.token(), issued.expiresAt())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Consume a password reset token and set the account's new password hash")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank token / password hash)"),
        @ApiResponse(responseCode = "404", description = "Token is invalid, expired, or already used")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.resetPassword(request.token(), request.passwordHash());
    }
}
