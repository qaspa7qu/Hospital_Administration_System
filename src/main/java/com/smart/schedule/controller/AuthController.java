package com.smart.schedule.controller;

import com.smart.schedule.dto.auth.LoginRequest;
import com.smart.schedule.dto.auth.LoginResponse;
import com.smart.schedule.dto.auth.UserInfoResponse;
import com.smart.schedule.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserInfoResponse currentUser(Authentication authentication) {
        return authService.getCurrentUser(authentication.getName());
    }
}
