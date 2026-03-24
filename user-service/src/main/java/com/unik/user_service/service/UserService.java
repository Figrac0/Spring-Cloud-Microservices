package com.unik.user_service.service;

import com.unik.user_service.client.CompanyClient;
import com.unik.user_service.domain.UserEntity;
import com.unik.user_service.dto.UserCreateRequest;
import com.unik.user_service.dto.UserResponse;
import com.unik.user_service.dto.UserUpdateRequest;
import com.unik.user_service.error.EntityNotFoundException;
import com.unik.user_service.messaging.event.CompanyDeletionCompletedEvent;
import com.unik.user_service.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final CompanyClient companyClient;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(
            UserRepository userRepository,
            CompanyClient companyClient,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.companyClient = companyClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponseWithCompanyName)
                .toList();
    }

    @Transactional
    public UserResponse create(UserCreateRequest req) {
        if (req.getCompanyId() != null && !companyClient.existsCompany(req.getCompanyId())) {
            throw new EntityNotFoundException("Company with id=" + req.getCompanyId() + " not found");
        }

        UserEntity e = new UserEntity();
        e.setName(req.getName());
        e.setLogin(req.getLogin());
        e.setPassword(req.getPassword());
        e.setEmail(req.getEmail());
        e.setCompanyId(req.getCompanyId());
        e.setActive(true);

        UserEntity saved = userRepository.save(e);
        return toResponseWithCompanyName(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest req) {
        UserEntity e = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User with id=" + id + " not found"));

        if (req.getCompanyId() != null && !companyClient.existsCompany(req.getCompanyId())) {
            throw new EntityNotFoundException("Company with id=" + req.getCompanyId() + " not found");
        }

        e.setName(req.getName());
        e.setEmail(req.getEmail());
        e.setCompanyId(req.getCompanyId());

        UserEntity saved = userRepository.save(e);
        return toResponseWithCompanyName(saved);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        UserEntity e = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User with id=" + id + " not found"));
        e.setActive(active);
        userRepository.save(e);
    }

    @Transactional(readOnly = true)
    public void assertExistsActive(Long id) {
        boolean ok = userRepository.existsByIdAndActiveTrue(id);
        if (!ok) {
            throw new EntityNotFoundException("Active user with id=" + id + " not found");
        }
    }

    @Transactional(readOnly = true)
    public String getActiveUserName(Long id) {
        UserEntity e = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("Active user with id=" + id + " not found"));
        return e.getName();
    }

    @Transactional
    public void detachUsersFromDeletedCompany(Long companyId) {
        int updatedUsers = userRepository.clearCompanyIdByCompanyId(companyId);
        log.info("Detached {} users from companyId={}", updatedUsers, companyId);
        eventPublisher.publishEvent(new CompanyDeletionCompletedEvent(companyId));
    }

    private UserResponse toResponseWithCompanyName(UserEntity e) {
        String companyName = companyClient.getCompanyNameOrNull(e.getCompanyId());
        return new UserResponse(
                e.getId(),
                e.getName(),
                e.getLogin(),
                e.getEmail(),
                e.isActive(),
                e.getCompanyId(),
                companyName);
    }
}
