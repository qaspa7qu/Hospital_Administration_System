package com.smart.schedule.dto.error;

public record ApiErrorResponse(
        int status,
        String error,
        String message
) {
}
