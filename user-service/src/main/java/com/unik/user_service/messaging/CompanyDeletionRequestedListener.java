package com.unik.user_service.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unik.user_service.messaging.event.CompanyDeletionRequestedEvent;
import com.unik.user_service.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CompanyDeletionRequestedListener {
    private static final Logger log = LoggerFactory.getLogger(CompanyDeletionRequestedListener.class);

    private final ObjectMapper objectMapper;
    private final UserService userService;

    public CompanyDeletionRequestedListener(ObjectMapper objectMapper, UserService userService) {
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.company-deletion-requested}",
            groupId = "${app.kafka.consumer-group}")
    public void onMessage(String payload) {
        try {
            CompanyDeletionRequestedEvent event = objectMapper.readValue(payload, CompanyDeletionRequestedEvent.class);
            log.info("Received company deletion request for companyId={}", event.companyId());
            userService.detachUsersFromDeletedCompany(event.companyId());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize company deletion request event", ex);
        }
    }
}
