package com.mhm.bank.service;

import com.mhm.bank.config.KeycloakTokenProvider;
import com.mhm.bank.controller.dto.LoginRequest;
import com.mhm.bank.controller.dto.TokensUser;
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
    @Mock
    private KeycloakTokenProvider tokenProvider;
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
    void shouldGetTokenAuthSuccessfully() throws KeycloakException {
        String expectedToken = "test-token";
        when(tokenProvider.getAccessToken()).thenReturn(expectedToken);

        String result = authService.getTokenAuth();
        assertEquals(expectedToken, result);

        verify(tokenProvider).getAccessToken();
    }

    @Test
    void shouldRegisterUserSuccessfully() throws UserAlreadyExistsException, KeycloakException, KafkaException {
        String mockToken = "test-token";
        when(tokenProvider.getAccessToken()).thenReturn(mockToken);

        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);
        when(keycloakService.createUser(any(), eq("Bearer " + mockToken))).thenReturn(true);

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

        RecordMetadata recordMetadata = new RecordMetadata(
                new TopicPartition("topic", 0),
                0L, 0, 0, 0L, 0, 0
        );

        SendResult<String, UserRegisteredEvent> sendResult = new SendResult<>(producerRecord, recordMetadata);
        when(kafkaProducerService.sendMessage(any(UserRegisteredEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        String result = authService.registerUser(userInformation);

        String expectedResult = "User testuser with ID test-id-1 has been added";
        assertEquals(expectedResult, result);

        verify(tokenProvider).getAccessToken();
        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository).existsByUsername(userInformation.username());
        verify(userRepository).existsByEmail(userInformation.email());
        verify(userRepository).save(any(UserEntity.class));
        verify(keycloakService).createUser(any(), eq("Bearer " + mockToken));
        verify(kafkaProducerService).sendMessage(any(UserRegisteredEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenUserIdAlreadyExists() throws KeycloakException {
        String mockToken = "test-token";
        when(tokenProvider.getAccessToken()).thenReturn(mockToken);
        when(userRepository.existsById(userInformation.idCard())).thenReturn(true);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () -> {
            authService.registerUser(userInformation);
        });

        assertEquals("User with ID " + userInformation.idCard() + " already exists", exception.getMessage());

        verify(tokenProvider).getAccessToken();
        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(keycloakService, never()).createUser(any(), anyString());
        verify(kafkaProducerService, never()).sendMessage(any(UserRegisteredEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenUsernameAlreadyExists() throws KeycloakException {
        String mockToken = "test-token";
        when(tokenProvider.getAccessToken()).thenReturn(mockToken);
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(true);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () -> {
            authService.registerUser(userInformation);
        });

        assertEquals("Username " + userInformation.username() + " is already taken", exception.getMessage());

        verify(tokenProvider).getAccessToken();
        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository).existsByUsername(userInformation.username());
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(keycloakService, never()).createUser(any(), anyString());
        verify(kafkaProducerService, never()).sendMessage(any(UserRegisteredEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() throws KeycloakException {
        String mockToken = "test-token";
        when(tokenProvider.getAccessToken()).thenReturn(mockToken);
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(true);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () -> {
            authService.registerUser(userInformation);
        });

        assertEquals("Email " + userInformation.email() + " is already taken", exception.getMessage());

        verify(tokenProvider).getAccessToken();
        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository).existsByUsername(userInformation.username());
        verify(userRepository).existsByEmail(userInformation.email());
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(keycloakService, never()).createUser(any(), anyString());
        verify(kafkaProducerService, never()).sendMessage(any(UserRegisteredEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenKeycloakFails() throws KeycloakException {
        String mockToken = "test-token";
        when(tokenProvider.getAccessToken()).thenReturn(mockToken);
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(false);
        when(keycloakService.createUser(any(), eq("Bearer " + mockToken))).thenReturn(false);

        KeycloakException exception = assertThrows(KeycloakException.class, () -> {
            authService.registerUser(userInformation);
        });

        assertEquals("Failed to create user " + userInformation.username() + " in Keycloak", exception.getMessage());

        verify(tokenProvider).getAccessToken();
        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository).existsByUsername(userInformation.username());
        verify(userRepository).existsByEmail(userInformation.email());
        verify(keycloakService).createUser(any(), eq("Bearer " + mockToken));
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(kafkaProducerService, never()).sendMessage(any(UserRegisteredEvent.class));
    }

    @Test
    void shouldThrowExceptionAndRollbackWhenKafkaFails() throws KeycloakException {
        String mockToken = "test-token";
        when(tokenProvider.getAccessToken()).thenReturn(mockToken);
        when(userRepository.existsById(userInformation.idCard())).thenReturn(false);
        when(userRepository.existsByUsername(userInformation.username())).thenReturn(false);
        when(userRepository.existsByEmail(userInformation.email())).thenReturn(false);
        when(keycloakService.createUser(any(), eq("Bearer " + mockToken))).thenReturn(true);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);
        when(kafkaProducerService.sendMessage(any()))
                .thenReturn(CompletableFuture.failedFuture(new org.springframework.kafka.KafkaException("Failed to send message")));

        org.springframework.kafka.KafkaException exception = assertThrows(org.springframework.kafka.KafkaException.class, () -> {
            authService.registerUser(userInformation);
        });

        assertEquals("Failed to send Kafka message for user: " + userInformation.username(), exception.getMessage());

        verify(tokenProvider).getAccessToken();
        verify(userRepository).existsById(userInformation.idCard());
        verify(userRepository).existsByUsername(userInformation.username());
        verify(userRepository).existsByEmail(userInformation.email());
        verify(keycloakService).createUser(any(), eq("Bearer " + mockToken));
        verify(userRepository).save(any(UserEntity.class));
        verify(keycloakService).deleteUser(userInformation.username());
    }

    @Test
    void shouldLoginUserSuccessfully() throws KeycloakException {
        String mockToken = "test-token";
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");
        TokensUser expectedTokens = new TokensUser("access-token-123", "refresh-token-456");

        when(tokenProvider.getAccessToken()).thenReturn(mockToken);
        when(keycloakService.loginUser(loginRequest, mockToken)).thenReturn(expectedTokens);

        TokensUser result = authService.loginUser(loginRequest);

        assertEquals(expectedTokens.getAccessToken(), result.getAccessToken());
        assertEquals(expectedTokens.getRefreshToken(), result.getRefreshToken());
        verify(tokenProvider).getAccessToken();
        verify(keycloakService).loginUser(loginRequest, mockToken);
    }

    @Test
    void shouldThrowExceptionWhenLoginFails() throws KeycloakException {
        String mockToken = "test-token";
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");
        String errorMessage = "Authentication failed";

        when(tokenProvider.getAccessToken()).thenReturn(mockToken);
        when(keycloakService.loginUser(loginRequest, mockToken))
                .thenThrow(new KeycloakException(errorMessage));

        KeycloakException exception = assertThrows(KeycloakException.class, () ->
                authService.loginUser(loginRequest));

        assertEquals(errorMessage, exception.getMessage());
        verify(tokenProvider).getAccessToken();
        verify(keycloakService).loginUser(loginRequest, mockToken);
    }

    @Test
    void shouldThrowExceptionWhenTokenNotAvailable() throws KeycloakException {
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");
        when(tokenProvider.getAccessToken()).thenReturn(null);

        KeycloakException exception = assertThrows(KeycloakException.class, () ->
                authService.loginUser(loginRequest));

        assertEquals("Failed to obtain Keycloak token", exception.getMessage());
        verify(tokenProvider).getAccessToken();
        verify(keycloakService, never()).loginUser(any(), any());
    }

}