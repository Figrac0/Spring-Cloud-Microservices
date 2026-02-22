package com.unik.company_service.dto;

import jakarta.validation.constraints.*;

public class CompanyCreateRequest {
    @NotBlank
    private String name;

    @NotBlank
    @Size(max = 20)
    private String ogrn;

    private String activityDescription;

    @NotNull
    private Long directorId;

    public String getName() {
        return name;
    }

    public String getOgrn() {
        return ogrn;
    }

    public String getActivityDescription() {
        return activityDescription;
    }

    public Long getDirectorId() {
        return directorId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOgrn(String ogrn) {
        this.ogrn = ogrn;
    }

    public void setActivityDescription(String activityDescription) {
        this.activityDescription = activityDescription;
    }

    public void setDirectorId(Long directorId) {
        this.directorId = directorId;
    }
}