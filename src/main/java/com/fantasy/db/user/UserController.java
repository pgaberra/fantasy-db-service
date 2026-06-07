package com.fantasy.db.user;

import com.fantasy.db.exception.UserNotFoundException;
import com.fantasy.db.user.dto.CreateUserRequest;
import com.fantasy.db.user.dto.ExistsResponse;
import com.fantasy.db.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
                .orElseThrow(() -> new UserNotFoundException(email));
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
}
