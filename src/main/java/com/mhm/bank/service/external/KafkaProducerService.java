package com.mhm.bank.service.external;

import com.mhm.bank.controller.dto.UserRegisteredEvent;
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
    @Value("${spring.kafka.producer.topic.name}")
    private String topic;

    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        logger.info("KafkaProducerService initialized with topic: {}", topic);
    }

    public CompletableFuture<SendResult<String, UserRegisteredEvent>> sendMessage(UserRegisteredEvent event) {
        logger.debug("Attempting to send message for user: {}", event != null ? event.username() : "null");

        if (event == null || event.userId() == null) {
            logger.error("Invalid event: event or userId is null");
            throw new IllegalArgumentException("Event or userId cannot be null");
        }

        logger.info("Sending message to topic {} for user: {}", topic, event.username());
        return kafkaTemplate.send(topic, event.userId(), event)
                .orTimeout(serviceTimeout, TimeUnit.SECONDS)
                .thenApply(result -> {
                    logger.info("Message sent successfully to topic: {}, partition: {}, offset: {}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    return result;
                })
                .exceptionally(throwable -> {
                    String errorMessage = String.format("Failed to send message for user %s: %s",
                            event.username(), throwable.getMessage());
                    logger.error("Kafka error: {}", errorMessage, throwable);
                    throw new KafkaException(errorMessage, throwable);
                });
    }


}
