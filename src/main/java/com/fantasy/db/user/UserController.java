package com.fantasy.db.user;

import com.fantasy.db.exception.UserNotFoundException;
import com.fantasy.db.user.dto.CreateUserRequest;
import com.fantasy.db.user.dto.ExistsResponse;
import com.fantasy.db.user.dto.UserResponse;
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

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse getByEmail(@RequestParam String email) {
        return userService.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    @GetMapping("/exists")
    public ExistsResponse existsByEmail(@RequestParam String email) {
        return new ExistsResponse(userService.existsByEmail(email));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.create(request.email(), request.passwordHash());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }
}
