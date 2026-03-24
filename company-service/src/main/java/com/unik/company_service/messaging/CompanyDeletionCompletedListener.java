package com.unik.company_service.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unik.company_service.messaging.event.CompanyDeletionCompletedEvent;
import com.unik.company_service.service.CompanyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CompanyDeletionCompletedListener {
    private static final Logger log = LoggerFactory.getLogger(CompanyDeletionCompletedListener.class);

    private final ObjectMapper objectMapper;
    private final CompanyService companyService;

    public CompanyDeletionCompletedListener(ObjectMapper objectMapper, CompanyService companyService) {
        this.objectMapper = objectMapper;
        this.companyService = companyService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.company-deletion-completed}",
            groupId = "${app.kafka.consumer-group}")
    public void onMessage(String payload) {
        try {
            CompanyDeletionCompletedEvent event = objectMapper.readValue(payload, CompanyDeletionCompletedEvent.class);
            log.info("Received company deletion completion event for companyId={}", event.companyId());
            companyService.physicallyDelete(event.companyId());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize company deletion completion event", ex);
        }
    }
}
