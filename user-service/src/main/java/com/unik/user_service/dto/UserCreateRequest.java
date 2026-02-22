package com.unik.user_service.dto;

import jakarta.validation.constraints.*;

public class UserCreateRequest {
    @NotBlank
    private String name;

    @NotBlank
    @Size(max = 100)
    private String login;

    @NotBlank
    private String password;

    @NotBlank
    @Email
    private String email;

    private Long companyId;

    public String getName() {
        return name;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }
}