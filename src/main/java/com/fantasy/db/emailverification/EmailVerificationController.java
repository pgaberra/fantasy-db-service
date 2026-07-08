package com.fantasy.db.emailverification;

import com.fantasy.db.emailverification.dto.CreateEmailVerificationTokenRequest;
import com.fantasy.db.emailverification.dto.EmailVerificationTokenResponse;
import com.fantasy.db.emailverification.dto.VerifyEmailRequest;
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

@Tag(name = "Email verification", description = "Single-use email-verification token issuance and consumption")
@RestController
@RequestMapping("/api/v1/users/email-verification")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @Operation(summary = "Issue a single-use email-verification token for the account with this email",
            description = "Returns 200 with a raw token when the email maps to an unverified account. "
                    + "Returns 204 when there is nothing to verify (unknown email, or an already-verified "
                    + "account). The caller must treat both outcomes identically so that account existence "
                    + "is never revealed to end users.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token issued"),
        @ApiResponse(responseCode = "204", description = "Nothing to verify; nothing issued"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank/invalid email)")
    })
    @PostMapping("/tokens")
    public ResponseEntity<EmailVerificationTokenResponse> issueToken(
            @Valid @RequestBody CreateEmailVerificationTokenRequest request) {
        return emailVerificationService.issueToken(request.email())
                .map(issued -> ResponseEntity.ok(
                        new EmailVerificationTokenResponse(issued.token(), issued.expiresAt())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Consume an email-verification token and mark the account's email verified")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Email verified"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank token)"),
        @ApiResponse(responseCode = "404", description = "Token is invalid, expired, or already used")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
    }
}
