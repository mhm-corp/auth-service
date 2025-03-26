package com.mhm.bank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mhm.bank.dto.UserInformation;
import com.mhm.bank.dto.UserRegisteredEvent;
import com.mhm.bank.repository.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AuthController authController;
    private KafkaConsumer<String, UserRegisteredEvent> consumer;
    @Autowired
    private UserRepository userRepository;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:15-alpine"
    ).withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.3.3")
                    .asCompatibleSubstituteFor("confluentinc/cp-kafka")
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", () -> "org.springframework.kafka.support.serializer.JsonSerializer");

    }

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Configure the Kafka consumer for testing
        setupKafkaConsumer();

    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }

        if (!postgres.isRunning()) {
            postgres.start();
        }

        if (!kafka.isRunning()) {
            kafka.start();
            setupKafkaConsumer();
        }
    }

    private void setupKafkaConsumer() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.TYPE_MAPPINGS, "userRegisteredEvent:com.mhm.bank.dto.UserRegisteredEvent");

        if (consumer != null) {
            consumer.close();
        }

        consumer = new KafkaConsumer<>(consumerProps, new StringDeserializer(),
                new JsonDeserializer<>(UserRegisteredEvent.class, false));
        consumer.subscribe(Collections.singletonList("user-registered"));
    }

    @Test
    void registerUser_shouldReturnBadRequest_whenInputIsInvalid() throws Exception {
        UserInformation userInformation = new UserInformation(
                null,
                null,
                "",
                "John",
                "Doe",
                "123 Main St",
                "invalid-email",
                null,
                "123456789"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userInformation)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_shouldSuccessfullyCreateUser_whenInputIsValid() throws Exception {
        UserInformation validUser = new UserInformation(
                "12345678",
                "username123",
                "Password123!",
                "John",
                "Doe",
                "123 Main St",
                "john.doe@example.com",
                LocalDate.parse("1990-01-01"),
                "123456789"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUser)))
                .andExpect(status().isCreated())
                .andExpect(content().string("User with ID 12345678 has been added"));

        ConsumerRecords<String, UserRegisteredEvent> records = consumer.poll(Duration.ofSeconds(10));
        assertFalse(records.isEmpty(), "No messages were received in Kafka");

        ConsumerRecord<String, UserRegisteredEvent> recordConsumer = records.iterator().next();
        UserRegisteredEvent event = recordConsumer.value();

        assertNotNull(event, "Kafka event cannot be null");
        assertEquals(validUser.idCard(), event.userId(), "User ID does not match");
        assertEquals(validUser.username(), event.username(), "Username does not match");
        assertEquals(validUser.email(), event.email(), "Email does not match");
    }

    @Test
    void registerUser_shouldFailWithInternalServerError_whenDatabaseIsDown() throws Exception {
        postgres.stop();

        UserInformation validUser = new UserInformation(
                "234567890",
                "username234",
                "Password234!",
                "John",
                "Doe",
                "123 Main St",
                "john.doe@example.com",
                LocalDate.parse("1990-01-01"),
                "123456789"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUser)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("DATABASE_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("Error accessing the database")));

        ConsumerRecords<String, UserRegisteredEvent> records = consumer.poll(Duration.ofSeconds(5));
        assertTrue(records.isEmpty(), "No messages should have been received in Kafka");

        postgres.start();
    }

    @Test
    void registerUser_shouldFailWithInternalServerError_whenKafkaIsDown() throws Exception {
        kafka.stop();

        UserInformation validUser = new UserInformation(
                "3456789012",
                "username345",
                "Password345!",
                "John",
                "Doe",
                "123 Main St",
                "john.doe@example.com",
                LocalDate.parse("1990-01-01"),
                "123456789"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUser)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("KAFKA_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("Error with message broker")));

        assertFalse(userRepository.existsById(validUser.idCard()),
                "User should not exist in database when Kafka is down");

        kafka.start();
        setupKafkaConsumer();
    }

}