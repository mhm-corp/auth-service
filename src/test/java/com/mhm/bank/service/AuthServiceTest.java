package com.mhm.bank.service;

import com.mhm.bank.dto.UserInformation;
import com.mhm.bank.dto.UserRegisteredEvent;
import com.mhm.bank.entity.UserEntity;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import com.mhm.bank.service.external.KafkaProducerService;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

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
    }

    @Test
    void shouldRegisterUserSuccessfully() throws UserAlreadyExistsException {
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        // Configure mock for Kafka producer
        CompletableFuture<SendResult<String, UserRegisteredEvent>> future = new CompletableFuture<>();
        future.complete(new SendResult<>(null, null));
        when(kafkaProducerService.sendMessage(any(UserRegisteredEvent.class))).thenReturn(future);

        String result = authService.registerUser(userInformation);

        String expectedResult = String.format("User with ID %s has been added", userInformation.idCard());
        assertEquals(expectedResult, result);

        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository).existsByUsername(userInformation.username());
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
    void shouldThrowExceptionWhenKafkaTimesOut() {
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        CompletableFuture<SendResult<String, UserRegisteredEvent>> future = new CompletableFuture<>();
        future.completeExceptionally(new TimeoutException("Kafka timeout"));
        when(kafkaProducerService.sendMessage(any(UserRegisteredEvent.class))).thenReturn(future);

        assertThrows(KafkaException.class, () -> authService.registerUser(userInformation));

        verify(userRepository).save(any(UserEntity.class));
        verify(kafkaProducerService).sendMessage(any(UserRegisteredEvent.class));
    }

}