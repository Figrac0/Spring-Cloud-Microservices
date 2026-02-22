package com.unik.company_service.service;

import com.unik.company_service.client.UserClient;
import com.unik.company_service.domain.CompanyEntity;
import com.unik.company_service.dto.CompanyCreateRequest;
import com.unik.company_service.dto.CompanyResponse;
import com.unik.company_service.error.EntityNotFoundException;
import com.unik.company_service.repo.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserClient userClient;

    public CompanyService(CompanyRepository companyRepository, UserClient userClient) {
        this.companyRepository = companyRepository;
        this.userClient = userClient;
    }

    @Transactional(readOnly = true)
    public void assertExists(Long id) {
        boolean ok = companyRepository.existsById(id);
        if (!ok) {
            throw new EntityNotFoundException("Company with id=" + id + " not found");
        }
    }

    @Transactional(readOnly = true)
    public String getCompanyName(Long id) {
        CompanyEntity e = companyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company with id=" + id + " not found"));
        return e.getName();
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> findAll() {
        return companyRepository.findAll().stream()
                .map(this::toResponseWithDirectorName)
                .toList();
    }

    @Transactional
    public CompanyResponse create(CompanyCreateRequest req) {
        if (!userClient.existsActiveUser(req.getDirectorId())) {
            throw new EntityNotFoundException("Active director user with id=" + req.getDirectorId() + " not found");
        }

        CompanyEntity e = new CompanyEntity();
        e.setName(req.getName());
        e.setOgrn(req.getOgrn());
        e.setActivityDescription(req.getActivityDescription());
        e.setDirectorId(req.getDirectorId());

        CompanyEntity saved = companyRepository.save(e);
        return toResponseWithDirectorName(saved);
    }

    private CompanyResponse toResponseWithDirectorName(CompanyEntity e) {
        String directorName = userClient.getUserNameOrNull(e.getDirectorId());
        return new CompanyResponse(
                e.getId(),
                e.getName(),
                e.getOgrn(),
                e.getActivityDescription(),
                e.getDirectorId(),
                directorName);
    }
}