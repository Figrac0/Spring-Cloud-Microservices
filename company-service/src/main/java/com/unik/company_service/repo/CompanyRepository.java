package com.unik.company_service.repo;

import com.unik.company_service.domain.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {
    Optional<CompanyEntity> findByIdAndDeletedFalse(Long id);

    boolean existsByIdAndDeletedFalse(Long id);

    List<CompanyEntity> findAllByDeletedFalse();

    @Modifying
    @Query("delete from CompanyEntity c where c.id = :id and c.deleted = true")
    int deleteSoftDeletedById(@Param("id") Long id);
}
