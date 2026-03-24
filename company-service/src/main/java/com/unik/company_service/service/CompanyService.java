package com.unik.company_service.service;

import com.unik.company_service.client.UserClient;
import com.unik.company_service.domain.CompanyEntity;
import com.unik.company_service.dto.CompanyCreateRequest;
import com.unik.company_service.dto.CompanyResponse;
import com.unik.company_service.error.EntityNotFoundException;
import com.unik.company_service.messaging.event.CompanyDeletionRequestedEvent;
import com.unik.company_service.repo.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository companyRepository;
    private final UserClient userClient;
    private final ApplicationEventPublisher eventPublisher;

    public CompanyService(
            CompanyRepository companyRepository,
            UserClient userClient,
            ApplicationEventPublisher eventPublisher) {
        this.companyRepository = companyRepository;
        this.userClient = userClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public void assertExists(Long id) {
        boolean ok = companyRepository.existsByIdAndDeletedFalse(id);
        if (!ok) {
            throw new EntityNotFoundException("Company with id=" + id + " not found");
        }
    }

    @Transactional(readOnly = true)
    public String getCompanyName(Long id) {
        CompanyEntity e = companyRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Company with id=" + id + " not found"));
        return e.getName();
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> findAll() {
        return companyRepository.findAllByDeletedFalse().stream()
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

    @Transactional
    public void delete(Long id) {
        CompanyEntity company = companyRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("Company with id=" + id + " not found"));

        company.setDeleted(true);
        companyRepository.save(company);

        eventPublisher.publishEvent(new CompanyDeletionRequestedEvent(company.getId()));
        log.info("Company with id={} marked as deleted", company.getId());
    }

    @Transactional
    public void physicallyDelete(Long id) {
        int deletedCount = companyRepository.deleteSoftDeletedById(id);
        if (deletedCount > 0) {
            log.info("Company with id={} physically deleted from database", id);
        } else {
            log.info("Physical delete skipped for company id={} because the company is already absent", id);
        }
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
