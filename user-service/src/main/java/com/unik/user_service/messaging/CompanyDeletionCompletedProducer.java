package com.unik.user_service.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unik.user_service.messaging.event.CompanyDeletionCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CompanyDeletionCompletedProducer {
    private static final Logger log = LoggerFactory.getLogger(CompanyDeletionCompletedProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public CompanyDeletionCompletedProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.company-deletion-completed}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompanyDeletionCompleted(CompanyDeletionCompletedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, String.valueOf(event.companyId()), payload);
            log.info("Sent company deletion completion to Kafka for companyId={}", event.companyId());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize company deletion completion event", ex);
        }
    }
}
