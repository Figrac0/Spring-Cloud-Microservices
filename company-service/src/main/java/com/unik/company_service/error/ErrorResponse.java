package com.unik.company_service.error;

import java.time.OffsetDateTime;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        OffsetDateTime timestamp) {
}