package com.smart.schedule.service;

import com.smart.schedule.dto.auth.LoginRequest;
import com.smart.schedule.dto.auth.LoginResponse;
import com.smart.schedule.dto.auth.UserInfoResponse;
import com.smart.schedule.entity.User;
import com.smart.schedule.repository.UserRepository;
import com.smart.schedule.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String accessToken = jwtService.generateToken(user);
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds(),
                toUserInfo(user));
    }

    public UserInfoResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toUserInfo(user);
    }

    private UserInfoResponse toUserInfo(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new UserInfoResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                roles);
    }
}
