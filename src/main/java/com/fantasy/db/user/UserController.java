package com.fantasy.db.user;

import com.fantasy.db.user.dto.CreateUserRequest;
import com.fantasy.db.user.dto.SetUsernameRequest;
import com.fantasy.db.user.dto.ExistsResponse;
import com.fantasy.db.user.dto.FacebookUserRequest;
import com.fantasy.db.user.dto.GoogleUserRequest;
import com.fantasy.db.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import java.util.NoSuchElementException;

@Tag(name = "Users", description = "User management")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Look up a user by email address")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "No user with that email")
    })
    @GetMapping
    public UserResponse getByEmail(@RequestParam String email) {
        return userService.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No user with email: " + email));
    }

    @Operation(summary = "Check whether a user with the given email exists")
    @ApiResponse(responseCode = "200", description = "Always returns 200; check the 'exists' field")
    @GetMapping("/exists")
    public ExistsResponse existsByEmail(@RequestParam String email) {
        return new ExistsResponse(userService.existsByEmail(email));
    }

    @Operation(summary = "Create a new user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank email / password hash)"),
        @ApiResponse(responseCode = "409", description = "A user with that email already exists")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.create(request.email(), request.passwordHash());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @Operation(summary = "Resolve the account for a verified Google identity",
            description = "Returns the user linked to this Google subject, linking it to an "
                    + "existing account with the same email or creating a new password-less user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User resolved (found, linked, or created)"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank email / subject)")
    })
    @PostMapping("/google")
    public UserResponse findOrCreateGoogleUser(@Valid @RequestBody GoogleUserRequest request) {
        return UserResponse.from(userService.findOrCreateGoogleUser(request.email(), request.googleSub()));
    }

    @Operation(summary = "Resolve the account for a verified Facebook identity",
            description = "Returns the user linked to this Facebook subject, linking it to an "
                    + "existing account with the same email or creating a new password-less user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User resolved (found, linked, or created)"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank email / subject)")
    })
    @PostMapping("/facebook")
    public UserResponse findOrCreateFacebookUser(@Valid @RequestBody FacebookUserRequest request) {
        return UserResponse.from(
                userService.findOrCreateFacebookUser(request.email(), request.facebookSub()));
    }
    @Operation(summary = "Set the account's public name",
            description = "Nullable on the account until it is set; sharing a projection requires it.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Username set"),
        @ApiResponse(responseCode = "400", description = "Validation failed (length or characters)"),
        @ApiResponse(responseCode = "404", description = "No such user"),
        @ApiResponse(responseCode = "409", description = "Another account already holds that name")
    })
    @PutMapping("/{userId}/username")
    public UserResponse setUsername(@PathVariable UUID userId,
                                    @Valid @RequestBody SetUsernameRequest request) {
        return UserResponse.from(userService.setUsername(userId, request.username()));
    }

}
