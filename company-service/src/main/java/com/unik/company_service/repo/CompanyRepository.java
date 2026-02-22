package com.unik.company_service.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.unik.company_service.domain.CompanyEntity;

public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {
}