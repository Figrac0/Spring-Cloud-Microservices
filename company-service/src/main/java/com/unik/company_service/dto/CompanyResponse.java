package com.unik.company_service.dto;

public class CompanyResponse {
    private Long id;
    private String name;
    private String ogrn;
    private String activityDescription;
    private Long directorId;
    private String directorFullName;

    public CompanyResponse(Long id, String name, String ogrn, String activityDescription, Long directorId,
            String directorFullName) {
        this.id = id;
        this.name = name;
        this.ogrn = ogrn;
        this.activityDescription = activityDescription;
        this.directorId = directorId;
        this.directorFullName = directorFullName;
    }

    public Long getId() {
        return id;
    }

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

    public String getDirectorFullName() {
        return directorFullName;
    }
}