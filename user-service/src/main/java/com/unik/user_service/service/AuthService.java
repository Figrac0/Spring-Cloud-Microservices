package com.unik.user_service.service;

import com.unik.user_service.dto.AuthLoginRequest;
import com.unik.user_service.dto.AuthRegisterRequest;
import com.unik.user_service.dto.AuthResponse;
import com.unik.user_service.dto.UserResponse;
import com.unik.user_service.security.AppUserDetailsService;
import com.unik.user_service.security.JwtTokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {
    private final UserService userService;
    private final AppUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserService userService,
            AppUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public UserResponse register(AuthRegisterRequest request) {
        return userService.register(request);
    }

    public AuthResponse login(AuthLoginRequest request) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getLogin());
        if (!userDetails.isEnabled() || !passwordEncoder.matches(request.getPassword(), userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid login or password");
        }

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .toList();

        String token = jwtTokenService.generateToken(userDetails.getUsername(), roles);
        return new AuthResponse(token, "Bearer", userDetails.getUsername(), roles, jwtTokenService.getExpiration());
    }
}
