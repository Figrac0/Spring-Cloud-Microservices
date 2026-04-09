package com.unik.api_gateway.security;

import java.util.List;

public record JwtPayload(String username, List<String> roles) {
}
