package com.unik.user_service.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.unik.user_service.domain.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByIdAndActiveTrue(Long id);

    boolean existsByIdAndActiveTrue(Long id);
}