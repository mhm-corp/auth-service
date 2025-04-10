package com.mhm.bank.service.external;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.TopicListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class KafkaTopicService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaTopicService.class);
    private final KafkaAdmin kafkaAdmin;
    private static final int timeout = 10;

    public KafkaTopicService(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    public boolean topicExists(String topicName) {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            ListTopicsResult topics = adminClient.listTopics();
            Set<String> topicNames = topics.names().get(timeout, TimeUnit.SECONDS);
            return topicNames.contains(topicName);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Error checking topic existence: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public Set<String> getAllTopics() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            ListTopicsResult topics = adminClient.listTopics();
            Collection<TopicListing> topicListings = topics.listings().get(timeout, TimeUnit.SECONDS);
            return topicListings.stream()
                    .map(TopicListing::name)
                    .collect(java.util.stream.Collectors.toSet());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Error retrieving topics: {}", e.getMessage());
            Thread.currentThread().interrupt();
            throw new org.springframework.kafka.KafkaException("Failed to retrieve Kafka topics", e);
        }
    }
}
