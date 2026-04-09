package com.unik.user_service.client;

import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "company-service-client",
        url = "${external.company-service.base-url}",
        configuration = FeignSecurityConfiguration.class)
public interface CompanyClient {

    @GetMapping("/exists/{id}")
    ResponseEntity<Void> assertExists(@PathVariable("id") Long id);

    @GetMapping("/{id}/name")
    String getCompanyName(@PathVariable("id") Long id);

    default boolean existsCompany(Long companyId) {
        if (companyId == null) {
            return true;
        }
        try {
            assertExists(companyId);
            return true;
        } catch (FeignException.NotFound ex) {
            return false;
        }
    }

    default String getCompanyNameOrNull(Long companyId) {
        if (companyId == null) {
            return null;
        }
        try {
            return getCompanyName(companyId);
        } catch (FeignException.NotFound ex) {
            return null;
        }
    }
}
