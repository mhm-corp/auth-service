package com.mhm.bank.service;

import com.mhm.bank.controller.dto.UserInformation;
import com.mhm.bank.controller.dto.UserRegisteredEvent;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import com.mhm.bank.repository.entity.UserEntity;
import com.mhm.bank.service.external.IKeycloakService;
import com.mhm.bank.service.external.KafkaProducerService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private KafkaProducerService kafkaProducerService;
    @Mock
    private IKeycloakService keycloakService;

    @InjectMocks
    private AuthService authService;

    private UserInformation userInformation;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        userInformation = new UserInformation(
                "test-id-1",
                "testuser",
                "password123",
                "John",
                "Doe",
                "123 Test St",
                "test@example.com",
                LocalDate.of(1990, 1, 1),
                "1234567890"
        );

        userEntity = new UserEntity();
        userEntity.setId(userInformation.idCard());
        userEntity.setUsername(userInformation.username());
        userEntity.setFirstName(userInformation.firstName());
        userEntity.setLastName(userInformation.lastName());
        userEntity.setEmail(userInformation.email());
        userEntity.setAddress(userInformation.address());
        userEntity.setPhoneNumber(userInformation.phoneNumber());
        userEntity.setBirthDate(userInformation.birthdate());

        //For Keycloak
        ReflectionTestUtils.setField(authService, "authTimeout", 30);
        ReflectionTestUtils.setField(authService, "kcUserRole", "user");
    }

    @Test
    void shouldRegisterUserSuccessfully() throws UserAlreadyExistsException, KeycloakException {
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);
        when(keycloakService.createUser(any(), anyString())).thenReturn(true);

        // Create producer record
        ProducerRecord<String, UserRegisteredEvent> producerRecord =
                new ProducerRecord<>("topic", userInformation.username(), new UserRegisteredEvent(
                        userInformation.idCard(),
                        userInformation.username(),
                        userInformation.firstName(),
                        userInformation.lastName(),
                        userInformation.email(),
                        userInformation.address(),
                        userInformation.phoneNumber(),
                        userInformation.birthdate().toString()
                ));

        // Create record metadata with correct parameters
        RecordMetadata recordMetadata = new RecordMetadata(
                new TopicPartition("topic", 0),
                0L,
                0,
                0,
                0L,
                0,
                0
        );

        SendResult<String, UserRegisteredEvent> sendResult = new SendResult<>(producerRecord, recordMetadata);

        when(kafkaProducerService.sendMessage(any(UserRegisteredEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        String result = authService.registerUser(userInformation);

        String expectedResult = "User testuser with ID test-id-1 has been added";
        assertEquals(expectedResult, result);

        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository).existsByUsername(userInformation.username());
        verify(userRepository).existsByEmail(userInformation.email());
        verify(userRepository).save(any(UserEntity.class));
        verify(kafkaProducerService).sendMessage(any(UserRegisteredEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenUserIdAlreadyExists() {
        when(userRepository.existsById(userInformation.idCard())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> {
            authService.registerUser(userInformation);
        });

        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(kafkaProducerService, never()).sendMessage(any(UserRegisteredEvent.class));

    }

    @Test
    void shouldThrowExceptionWhenUsernameAlreadyExists() {
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> {
            authService.registerUser(userInformation);
        });

        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository).existsByUsername(userInformation.username());
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(kafkaProducerService, never()).sendMessage(any(UserRegisteredEvent.class));

    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> {
            authService.registerUser(userInformation);
        });

        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository).existsByUsername(userInformation.username());
        verify(userRepository).existsByEmail(userInformation.email());
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(kafkaProducerService, never()).sendMessage(any(UserRegisteredEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenKeycloakFails() throws KeycloakException {
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(false);
        when(keycloakService.createUser(any(),anyString())).thenReturn(false);

        assertThrows(KeycloakException.class, () -> {
            authService.registerUser(userInformation);
        });

        verify(userRepository, never()).save(any(UserEntity.class));
        verify(kafkaProducerService, never()).sendMessage(any(UserRegisteredEvent.class));
    }

    @Test
    void shouldThrowExceptionAndRollbackWhenKafkaFails() throws KeycloakException {
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(false);
        when(keycloakService.createUser(any(),anyString())).thenReturn(true);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);
        doThrow(new KafkaException("Kafka error")).when(kafkaProducerService).sendMessage(any());

        assertThrows(KafkaException.class, () -> {
            authService.registerUser(userInformation);
        });

        verify(userRepository).save(any(UserEntity.class));
        verify(keycloakService).deleteUser(userInformation.username());
    }

}