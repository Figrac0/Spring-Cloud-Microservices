package com.unik.user_service.dto;

import java.util.List;

public class UserResponse {
    private Long id;
    private String name;
    private String login;
    private String email;
    private boolean active;
    private Long companyId;
    private String companyName;
    private List<String> roles;

    public UserResponse(Long id, String name, String login, String email, boolean active, Long companyId,
            String companyName, List<String> roles) {
        this.id = id;
        this.name = name;
        this.login = login;
        this.email = email;
        this.active = active;
        this.companyId = companyId;
        this.companyName = companyName;
        this.roles = roles;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLogin() {
        return login;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public List<String> getRoles() {
        return roles;
    }
}
