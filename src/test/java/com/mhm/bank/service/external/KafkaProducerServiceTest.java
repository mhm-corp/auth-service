package com.mhm.bank.service.external;

import org.junit.jupiter.api.BeforeEach;
import com.mhm.bank.controller.dto.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;

    @Mock
    private SendResult<String, UserRegisteredEvent> sendResult;

    @InjectMocks
    private KafkaProducerService kafkaProducerService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kafkaProducerService, "serviceTimeout", 5);
    }

    private UserRegisteredEvent createDataUserRegisteredEvent (boolean isNullUserId) {
        String userId = isNullUserId ? null : "123";
        return new UserRegisteredEvent(
                userId,           // userId
                "testUser",      // username
                "John",          // firstName
                "Doe",           // lastName
                "test@email.com",// email
                "123 Main St",   // address
                "1234567890",    // phoneNumber
                "1990-01-01"     // birthDate
        );
    }

    @Test
    void sendMessage_Success() {
        UserRegisteredEvent event = createDataUserRegisteredEvent(false);
        when(kafkaTemplate.send(anyString(), anyString(), any(UserRegisteredEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        CompletableFuture<SendResult<String, UserRegisteredEvent>> future = kafkaProducerService.sendMessage(event);

        assertNotNull(future);
        assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void sendMessage_NullEvent_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> kafkaProducerService.sendMessage(null));
    }

    @Test
    void sendMessage_NullUserId_ThrowsException() {
        UserRegisteredEvent event = createDataUserRegisteredEvent(true);

        assertThrows(IllegalArgumentException.class, () -> kafkaProducerService.sendMessage(event));
    }

    @Test
    void sendMessage_KafkaFailure() {
        UserRegisteredEvent event = createDataUserRegisteredEvent(false);

        when(kafkaTemplate.send(anyString(), anyString(), any(UserRegisteredEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka error")));

        CompletableFuture<SendResult<String, UserRegisteredEvent>> future = kafkaProducerService.sendMessage(event);

        assertNotNull(future);
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> future.get(1, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof KafkaException);
    }



}