package com.unik.api_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

@Component
public class JwtTokenValidator {
    private final JwtProperties jwtProperties;

    public JwtTokenValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public JwtPayload validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String username = claims.get("username", String.class);
        Object rolesClaim = claims.get("roles");
        List<String> roles = rolesClaim instanceof Collection<?> collection
                ? collection.stream().map(String::valueOf).map(String::trim).filter(role -> !role.isBlank()).toList()
                : List.of();

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("JWT does not contain username claim");
        }

        return new JwtPayload(username, roles);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
