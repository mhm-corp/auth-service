package com.mhm.bank.service;

import com.mhm.bank.config.TokenProvider;
import com.mhm.bank.controller.dto.*;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import com.mhm.bank.repository.entity.UserEntity;
import com.mhm.bank.service.external.KafkaProducerService;
import com.mhm.bank.service.external.keycloak.IKeycloakService;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.token.TokenService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.support.SendResult;


@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private KafkaProducerService kafkaProducerService;
    @Mock
    private IKeycloakService keycloakService;
    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private TokenService tokenService;
    @Mock
    private UserDataAccessService userDataAccessService;
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
                "1234567890",
                null
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

        ReflectionTestUtils.setField(authService, "authTimeout", 30);
        ReflectionTestUtils.setField(authService, "kcUserRole", "user");
    }

    @Test
    void loginUserSuccessfully() throws KeycloakException {
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");
        TokensUser expectedTokens = new TokensUser("access-token", "refresh-token", "3600");
        String adminToken = "admin-token";

        when(keycloakService.getTokenAdminAppAuth()).thenReturn(adminToken);
        when(keycloakService.loginUser(loginRequest, adminToken)).thenReturn(expectedTokens);

        TokensUser result = authService.loginUser(loginRequest);

        assertEquals(expectedTokens, result);
        verify(keycloakService).getTokenAdminAppAuth();
        verify(keycloakService).loginUser(loginRequest, adminToken);
    }

    @Test
    void loginUserFailsWithInvalidCredentials() throws KeycloakException {
        LoginRequest loginRequest = new LoginRequest("testuser", "wrongpass");
        when(keycloakService.getTokenAdminAppAuth())
                .thenThrow(new KeycloakException("Invalid credentials"));

        assertThrows(KeycloakException.class, () ->
                authService.loginUser(loginRequest));
    }

    @Test
    void getUserInformationSuccessfully() {
        String searchData = "testuser";
        UserData expectedData = new UserData(
                "test-id-1",    // id
                "testuser",     // username
                "John",         // firstName
                "Doe",         // lastName
                "test@example.com", // email
                "123 Test St",  // address
                LocalDate.of(1990, 1, 1), // birthDate
                "1234567890"    // phoneNumber
        );

        when(userDataAccessService.getUserInfo(searchData)).thenReturn(expectedData);

        UserData result = authService.getUserInformation(searchData);

        assertNotNull(result);
        assertEquals(expectedData, result);
        verify(userDataAccessService).getUserInfo(searchData);
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws KeycloakException {
        String refreshToken = "refresh-token-123";
        TokensUser expectedTokens = new TokensUser("new-access-token", "new-refresh-token", "3600");

        when(keycloakService.getNewToken(refreshToken)).thenReturn(expectedTokens);

        TokensUser result = authService.refreshToken(refreshToken);

        assertNotNull(result);
        assertEquals(expectedTokens.getAccessToken(), result.getAccessToken());
        assertEquals(expectedTokens.getRefreshToken(), result.getRefreshToken());
        verify(keycloakService).getNewToken(refreshToken);
    }

    @Test
    void shouldThrowExceptionWhenRefreshTokenFails() {
        String refreshToken = "invalid-refresh-token";
        when(keycloakService.getNewToken(refreshToken))
                .thenAnswer(invocation -> {
                    throw new KeycloakException("Invalid refresh token");
                });

        KeycloakException exception = assertThrows(KeycloakException.class, () ->
                authService.refreshToken(refreshToken));

        assertEquals("Invalid refresh token", exception.getMessage());
        verify(keycloakService).getNewToken(refreshToken);
    }
    @Test
    void registerUserFailsWhenUserExists() throws UserAlreadyExistsException, KeycloakException {
        doThrow(new UserAlreadyExistsException("User already exists"))
                .when(userDataAccessService)
                .doesUserExistInDataBase(userInformation);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class,
                () -> authService.registerUser(userInformation));

        assertEquals("User already exists", exception.getMessage());
        verify(userDataAccessService).doesUserExistInDataBase(userInformation);
        verify(keycloakService, never()).createUser(any(), any());
        verify(userDataAccessService, never()).sendUserToDataBase(any());
        verify(kafkaProducerService, never()).sendMessage(any());
    }

    @Test
    void registerUserFailsWhenKeycloakCreateFails() throws  KeycloakException {
        String token = "admin-token";
        when(keycloakService.getTokenAdminAppAuth()).thenReturn(token);
        when(keycloakService.createUser(any(), any())).thenReturn(false);

        assertThrows(KeycloakException.class, () ->
                authService.registerUser(userInformation));
    }

    @Test
    void getUserInformationWhenUserNotFound() {
        String searchData = "nonexistent";
        when(userDataAccessService.getUserInfo(searchData))
                .thenReturn(null);

        UserData result = authService.getUserInformation(searchData);
        assertNull(result);
    }

    @Test
    void registerUserRollbackWhenDatabaseFails() throws KeycloakException {
        String token = "admin-token";
        when(keycloakService.getTokenAdminAppAuth()).thenReturn(token);
        when(keycloakService.createUser(any(), any())).thenReturn(true);
        when(userDataAccessService.sendUserToDataBase(userInformation))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () ->
                authService.registerUser(userInformation));
        verify(keycloakService).deleteUser(userInformation.username());
    }

    @Test
    void registerUserSuccessfully() throws Exception, KeycloakException {
        String token = "admin-token";
        String expectedMessage = String.format("User %s with ID %s has been added", userInformation.username(), userInformation.idCard());

        when(keycloakService.getTokenAdminAppAuth()).thenReturn(token);
        when(keycloakService.createUser(any(), eq("Bearer " + token))).thenReturn(true);
        when(userDataAccessService.sendUserToDataBase(userInformation)).thenReturn(userEntity);

        UserRegisteredEvent event = new UserRegisteredEvent(
                userInformation.idCard(),
                userInformation.username(),
                userInformation.firstName(),
                userInformation.lastName(),
                userInformation.email(),
                userInformation.address(),
                userInformation.phoneNumber(),
                userInformation.birthdate().toString()
        );

        ProducerRecord<String, UserRegisteredEvent> record = new ProducerRecord<>("topic", "key", event);
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("topic", 0), // partition
                0L,    // offset
                0L,    // timestamp
                0L,    // serialized key size (changed to Long)
                0L,    // serialized value size (changed to Long)
                0,     // crc (int)
                0      // length (int)
        );
        SendResult<String, UserRegisteredEvent> sendResult = new SendResult<>(record, metadata);

        when(kafkaProducerService.sendMessage(any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        String result = authService.registerUser(userInformation);

        assertEquals(expectedMessage, result);
        verify(userDataAccessService).doesUserExistInDataBase(userInformation);
        verify(keycloakService).createUser(any(), eq("Bearer " + token));
        verify(userDataAccessService).sendUserToDataBase(userInformation);
        verify(kafkaProducerService).sendMessage(any());
    }

    @Test
    void registerUserFailsWithKafkaError() throws  KeycloakException {
        String token = "admin-token";
        when(keycloakService.getTokenAdminAppAuth()).thenReturn(token);
        when(keycloakService.createUser(any(), any())).thenReturn(true);
        when(userDataAccessService.sendUserToDataBase(userInformation)).thenReturn(userEntity);
        when(kafkaProducerService.sendMessage(any()))
                .thenThrow(new KafkaException("Kafka timeout"));

        assertThrows(KafkaException.class, () ->
                authService.registerUser(userInformation));
        verify(keycloakService).deleteUser(userInformation.username());
    }
}