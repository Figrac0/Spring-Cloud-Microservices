package com.unik.company_service.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "companies")
public class CompanyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String ogrn;

    @Column(name = "activity_description")
    private String activityDescription;

    @Column(name = "director_id", nullable = false)
    private Long directorId;

    @Column(nullable = false)
    private boolean deleted = false;

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

    public boolean isDeleted() {
        return deleted;
    }

    public void setId(Long id) {
        this.id = id;
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

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
