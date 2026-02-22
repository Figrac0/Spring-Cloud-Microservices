package com.unik.user_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class CompanyClient {
    private final WebClient webClient;

    public CompanyClient(@Value("${external.company-service.base-url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public boolean existsCompany(Long companyId) {
        if (companyId == null)
            return true;
        try {
            webClient.get()
                    .uri("/companies/exists/{id}", companyId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (WebClientResponseException.NotFound ex) {
            return false;
        }
    }

    public String getCompanyNameOrNull(Long companyId) {
        if (companyId == null)
            return null;
        try {
            return webClient.get()
                    .uri("/companies/{id}/name", companyId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException.NotFound ex) {
            return null;
        }
    }
}