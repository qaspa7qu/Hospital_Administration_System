package com.smart.schedule.dto.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserInfoResponse user
) {
}
