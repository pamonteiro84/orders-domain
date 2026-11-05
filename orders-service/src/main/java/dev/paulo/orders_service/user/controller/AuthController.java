package dev.paulo.orders_service.user.controller;

import dev.paulo.orders_service.user.dto.LoginRequest;
import dev.paulo.orders_service.user.dto.RegisterUserRequest;
import dev.paulo.orders_service.user.dto.TokenResponse;
import dev.paulo.orders_service.user.dto.UserResponse;
import dev.paulo.orders_service.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest userRequest) {
        UserResponse response = auth.register(userRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }
}
