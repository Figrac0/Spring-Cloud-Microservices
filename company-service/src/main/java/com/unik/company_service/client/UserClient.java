package com.unik.company_service.client;

import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service-client",
        url = "${external.user-service.base-url}",
        configuration = FeignSecurityConfiguration.class)
public interface UserClient {

    @GetMapping("/exists/{id}")
    ResponseEntity<Void> assertExistsActive(@PathVariable("id") Long id);

    @GetMapping("/{id}/name")
    String getUserName(@PathVariable("id") Long id);

    default boolean existsActiveUser(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            assertExistsActive(userId);
            return true;
        } catch (FeignException.NotFound ex) {
            return false;
        }
    }

    default String getUserNameOrNull(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return getUserName(userId);
        } catch (FeignException.NotFound ex) {
            return null;
        }
    }
}
