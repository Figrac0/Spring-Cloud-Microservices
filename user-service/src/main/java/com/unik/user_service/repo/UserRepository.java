package com.unik.user_service.repo;

import com.unik.user_service.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByLogin(String login);

    Optional<UserEntity> findByIdAndActiveTrue(Long id);

    boolean existsByIdAndActiveTrue(Long id);

    boolean existsByLogin(String login);

    boolean existsByEmail(String email);

    boolean existsByLoginAndIdNot(String login, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserEntity u set u.companyId = null where u.companyId = :companyId")
    int clearCompanyIdByCompanyId(@Param("companyId") Long companyId);
}
