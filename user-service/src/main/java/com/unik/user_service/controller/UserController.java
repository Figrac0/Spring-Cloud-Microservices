package com.unik.user_service.controller;

import com.unik.user_service.dto.UserCreateRequest;
import com.unik.user_service.dto.UserResponse;
import com.unik.user_service.dto.UserUpdateRequest;
import com.unik.user_service.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<UserResponse> getAll(HttpServletRequest request) {
        touchSecurityContext(request);
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@Valid @RequestBody UserCreateRequest req, HttpServletRequest request) {
        touchSecurityContext(request);
        return userService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req,
            HttpServletRequest request) {
        touchSecurityContext(request);
        return userService.update(id, req);
    }

    @PatchMapping("/{id}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void setActive(@PathVariable Long id, @RequestParam boolean value, HttpServletRequest request) {
        touchSecurityContext(request);
        userService.setActive(id, value);
    }

    @GetMapping("/exists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void existsActive(@PathVariable Long id, HttpServletRequest request) {
        touchSecurityContext(request);
        userService.assertExistsActive(id);
    }

    @GetMapping("/{id}/name")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public String getActiveUserName(@PathVariable Long id, HttpServletRequest request) {
        touchSecurityContext(request);
        return userService.getActiveUserName(id);
    }

    private void touchSecurityContext(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        request.isUserInRole("USER");
        request.isUserInRole("ADMIN");
        if (principal == null) {
            throw new IllegalStateException("Authenticated principal is required");
        }
    }
}
