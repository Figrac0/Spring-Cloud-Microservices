package com.unik.user_service.dto;

import java.util.List;

public record AuthResponse(
        String token,
        String tokenType,
        String username,
        List<String> roles,
        long expiresIn) {
}
