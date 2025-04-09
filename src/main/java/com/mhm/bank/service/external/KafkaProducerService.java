package com.mhm.bank.service.external;

import com.mhm.bank.dto.UserRegisteredEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class KafkaProducerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);
    @Value("${kafka.producer.service.timeout}")
    private int serviceTimeout;
    private static final String TOPIC = "user-registered";

    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, UserRegisteredEvent>> sendMessage(UserRegisteredEvent event) {
        try {
            return kafkaTemplate.send(TOPIC, event.userId(), event)
                    .orTimeout(serviceTimeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("For the user {} has failed to send message to Kafka: {}", event.username(), e.getMessage());
            throw new KafkaException("For the user {"+event.username()+"} has failed to send message to Kafka: " + e.getMessage());
        }
    }


}
