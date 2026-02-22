package com.unik.user_service.dto;

import jakarta.validation.constraints.*;

public class UserUpdateRequest {
    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    private Long companyId;

    public String getName() {
        return name;
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

    public void setEmail(String email) {
        this.email = email;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }
}