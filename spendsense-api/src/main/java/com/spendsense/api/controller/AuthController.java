package com.spendsense.api.controller;

import com.spendsense.api.dto.AuthRequest;
import com.spendsense.api.dto.AuthResponse;
import com.spendsense.api.entity.User;
import com.spendsense.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        User user = userService.registerUser(request.getEmail(), request.getPassword());
        String token = userService.authenticateUser(request.getEmail(), request.getPassword());
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .userId(user.getId())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        String token = userService.authenticateUser(request.getEmail(), request.getPassword());
        User user = userService.loadUserByEmail(request.getEmail());
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .userId(user.getId())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal User user) {
        AuthResponse response = AuthResponse.builder()
                .email(user.getEmail())
                .userId(user.getId())
                .build();
        return ResponseEntity.ok(response);
    }
}
