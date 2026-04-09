package com.unik.user_service.domain;

import jakarta.persistence.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String login;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false)
    private String roles = UserRole.USER.name();

    public Long getId() {
        return id;
    }

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

    public boolean isActive() {
        return active;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getRoles() {
        return roles;
    }

    public List<String> getRoleList() {
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    public void setId(Long id) {
        this.id = id;
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

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public void setRoleList(List<String> roles) {
        this.roles = roles.stream()
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }
}
