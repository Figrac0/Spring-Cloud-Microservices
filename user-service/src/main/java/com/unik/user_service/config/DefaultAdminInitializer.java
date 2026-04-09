package com.unik.user_service.config;

import com.unik.user_service.domain.UserEntity;
import com.unik.user_service.domain.UserRole;
import com.unik.user_service.repo.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class DefaultAdminInitializer {

    @Bean
    public ApplicationRunner seedDefaultAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByLogin("admin")) {
                return;
            }

            UserEntity admin = new UserEntity();
            admin.setName("Administrator");
            admin.setLogin("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@unik.local");
            admin.setActive(true);
            admin.setCompanyId(null);
            admin.setRoleList(List.of(UserRole.ADMIN.name(), UserRole.USER.name()));

            userRepository.save(admin);
        };
    }
}
