package com.unik.user_service.controller;

import com.unik.user_service.dto.UserCreateRequest;
import com.unik.user_service.dto.UserResponse;
import com.unik.user_service.dto.UserUpdateRequest;
import com.unik.user_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> getAll() {
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreateRequest req) {
        return userService.create(req);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        return userService.update(id, req);
    }

    @PatchMapping("/{id}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setActive(@PathVariable Long id, @RequestParam boolean value) {
        userService.setActive(id, value);
    }

    @GetMapping("/exists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void existsActive(@PathVariable Long id) {
        userService.assertExistsActive(id);
    }

    @GetMapping("/{id}/name")
    public String getActiveUserName(@PathVariable Long id) {
        return userService.getActiveUserName(id);
    }
}