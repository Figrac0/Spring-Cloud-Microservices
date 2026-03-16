package com.unik.company_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class UserClient {
    private final WebClient webClient;

    public UserClient(@Value("${external.user-service.base-url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public boolean existsActiveUser(Long userId) {
        try {
            webClient.get()
                    .uri("/exists/{id}", userId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (WebClientResponseException.NotFound ex) {
            return false;
        }
    }

    public String getUserNameOrNull(Long userId) {
        try {
            return webClient.get()
                    .uri("/{id}/name", userId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException.NotFound ex) {
            return null;
        }
    }
}
