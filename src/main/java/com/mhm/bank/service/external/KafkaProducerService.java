package com.mhm.bank.service.external;

import com.mhm.bank.dto.UserRegisteredEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);
    private static final String TOPIC = "user-registered";

    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(UserRegisteredEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event).get();
            logger.info("User registration event sent to Kafka for user: {}, email: {}"
                    , event.username(), event.email());
        } catch (Exception e) {
            logger.error("For the user {} has failed to send message to Kafka: {}", event.username(), e.getMessage());
            throw new KafkaException("For the user {"+event.username()+"} has failed to send message to Kafka: " + e.getMessage());
        }
    }


}
