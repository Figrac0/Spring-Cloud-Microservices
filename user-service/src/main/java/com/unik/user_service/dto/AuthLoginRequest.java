package com.unik.user_service.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthLoginRequest {
    @NotBlank
    private String login;

    @NotBlank
    private String password;

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
