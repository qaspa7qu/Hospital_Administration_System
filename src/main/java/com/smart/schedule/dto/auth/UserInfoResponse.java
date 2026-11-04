package com.smart.schedule.dto.auth;

import java.util.Set;

public record UserInfoResponse(
        Long id,
        String username,
        String fullName,
        Set<String> roles
) {
}
