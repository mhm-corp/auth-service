package com.mhm.bank.service;

import com.mhm.bank.config.TokenProvider;
import com.mhm.bank.controller.dto.*;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import com.mhm.bank.repository.entity.UserEntity;
import com.mhm.bank.service.external.keycloak.IKeycloakService;
import com.mhm.bank.service.external.KafkaProducerService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.token.TokenService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
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

        //For Keycloak
        ReflectionTestUtils.setField(authService, "authTimeout", 30);
        ReflectionTestUtils.setField(authService, "kcUserRole", "user");
    }

    @Test
    void shouldGetTokenAuthSuccessfully() throws KeycloakException {
        String expectedToken = "test-token";
        when(tokenProvider.getTokenAdminAppAuth()).thenReturn(expectedToken);

        String mockToken = tokenProvider.getTokenAdminAppAuth();
        assertEquals(expectedToken, mockToken);

        verify(tokenProvider).getTokenAdminAppAuth();
    }

    @Test
    void shouldRegisterUserSuccessfully() throws UserAlreadyExistsException, KeycloakException, KafkaException {
        String mockToken = "test-token";
        when(tokenProvider.getTokenAdminAppAuth()).thenReturn(mockToken);
        when(userDataAccessService.sendUserToDataBase(userInformation)).thenReturn(userEntity);

        UserKCDto expectedUserKC = new UserKCDto(
                userInformation.username(),
                userInformation.password(),
                userInformation.firstName(),
                userInformation.lastName(),
                userInformation.email(),
                Set.of("user")
        );

        when(keycloakService.createUser(any(UserKCDto.class), eq("Bearer " + mockToken))).thenReturn(true);

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
                0L, 0, 0L, 0, 0
        );
        SendResult<String, UserRegisteredEvent> sendResult = new SendResult<>(producerRecord, recordMetadata);
        when(kafkaProducerService.sendMessage(any(UserRegisteredEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        String result = authService.registerUser(userInformation);

        String expectedResult = "User testuser with ID test-id-1 has been added";
        assertEquals(expectedResult, result);

        verify(tokenProvider).getTokenAdminAppAuth();
        verify(userDataAccessService).sendUserToDataBase(userInformation);
        verify(keycloakService).createUser(argThat(userKC ->
                userKC.username().equals(expectedUserKC.username()) &&
                        userKC.password().equals(expectedUserKC.password()) &&
                        userKC.firstName().equals(expectedUserKC.firstName()) &&
                        userKC.lastName().equals(expectedUserKC.lastName()) &&
                        userKC.email().equals(expectedUserKC.email()) &&
                        userKC.roles().equals(expectedUserKC.roles())
        ), eq("Bearer " + mockToken));
        verify(kafkaProducerService).sendMessage(any(UserRegisteredEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenUserIdAlreadyExists() throws KeycloakException, UserAlreadyExistsException {
        String mockToken = "test-token";
        when(tokenProvider.getTokenAdminAppAuth()).thenReturn(mockToken);
        doThrow(new UserAlreadyExistsException("User with ID " + userInformation.idCard() + " already exists"))
                .when(userDataAccessService).doesUserExistInDataBase(userInformation);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () ->
                authService.registerUser(userInformation));

        assertEquals("User with ID " + userInformation.idCard() + " already exists", exception.getMessage());

        verify(tokenProvider).getTokenAdminAppAuth();
        verify(userDataAccessService).doesUserExistInDataBase(userInformation);
        verify(keycloakService, never()).createUser(any(), anyString());
        verify(userDataAccessService, never()).sendUserToDataBase(any());
        verify(kafkaProducerService, never()).sendMessage(any());
    }

    @Test
    void shouldThrowExceptionWhenUsernameAlreadyExists() throws KeycloakException, UserAlreadyExistsException {
        String mockToken = "test-token";
        when(tokenProvider.getTokenAdminAppAuth()).thenReturn(mockToken);
        doThrow(new UserAlreadyExistsException("Username " + userInformation.username() + " is already taken"))
                .when(userDataAccessService).doesUserExistInDataBase(userInformation);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () ->
                authService.registerUser(userInformation));

        assertEquals("Username " + userInformation.username() + " is already taken", exception.getMessage());

        verify(tokenProvider).getTokenAdminAppAuth();
        verify(userDataAccessService).doesUserExistInDataBase(userInformation);
        verify(keycloakService, never()).createUser(any(), anyString());
        verify(userDataAccessService, never()).sendUserToDataBase(any());
        verify(kafkaProducerService, never()).sendMessage(any());
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() throws KeycloakException, UserAlreadyExistsException {
        String mockToken = "test-token";
        when(tokenProvider.getTokenAdminAppAuth()).thenReturn(mockToken);
        doThrow(new UserAlreadyExistsException("Email " + userInformation.email() + " is already taken"))
                .when(userDataAccessService).doesUserExistInDataBase(userInformation);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () ->
                authService.registerUser(userInformation));

        assertEquals("Email " + userInformation.email() + " is already taken", exception.getMessage());

        verify(tokenProvider).getTokenAdminAppAuth();
        verify(userDataAccessService).doesUserExistInDataBase(userInformation);
        verify(keycloakService, never()).createUser(any(), anyString());
        verify(userDataAccessService, never()).sendUserToDataBase(any());
        verify(kafkaProducerService, never()).sendMessage(any());
    }

    @Test
    void shouldThrowExceptionWhenKeycloakFails() throws KeycloakException, UserAlreadyExistsException {
        String mockToken = "test-token";
        when(tokenProvider.getTokenAdminAppAuth()).thenReturn(mockToken);
        doNothing().when(userDataAccessService).doesUserExistInDataBase(userInformation);
        when(keycloakService.createUser(any(), eq("Bearer " + mockToken))).thenReturn(false);

        KeycloakException exception = assertThrows(KeycloakException.class, () ->
                authService.registerUser(userInformation));

        assertEquals("Failed to create user " + userInformation.username() + " in Keycloak", exception.getMessage());

        verify(tokenProvider).getTokenAdminAppAuth();
        verify(userDataAccessService).doesUserExistInDataBase(userInformation);
        verify(keycloakService).createUser(any(), eq("Bearer " + mockToken));
        verify(userDataAccessService, never()).sendUserToDataBase(any());
        verify(kafkaProducerService, never()).sendMessage(any());
    }

    @Test
    void shouldThrowExceptionAndRollbackWhenKafkaFails() throws KeycloakException {
        String mockToken = "test-token";
        when(tokenProvider.getTokenAdminAppAuth()).thenReturn(mockToken);
        when(userDataAccessService.sendUserToDataBase(userInformation)).thenReturn(userEntity);
        when(keycloakService.createUser(any(), eq("Bearer " + mockToken))).thenReturn(true);
        when(kafkaProducerService.sendMessage(any()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("Failed to send message")));

        KafkaException exception = assertThrows(KafkaException.class, () ->
                authService.registerUser(userInformation));

        assertEquals("Failed to send Kafka message for user: " + userInformation.username(), exception.getMessage());

        verify(tokenProvider).getTokenAdminAppAuth();
        verify(userDataAccessService).sendUserToDataBase(userInformation);
        verify(keycloakService).createUser(any(), eq("Bearer " + mockToken));
        verify(keycloakService).deleteUser(userInformation.username());
    }

    @Test
    void shouldLoginUserSuccessfully() throws KeycloakException {
        String mockToken = "test-token";
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");
        TokensUser expectedTokens = new TokensUser("access-token-123", "refresh-token-456");

        when(tokenProvider.getTokenAdminAppAuth()).thenReturn(mockToken);
        when(keycloakService.loginUser(loginRequest, mockToken)).thenReturn(expectedTokens);

        TokensUser result = authService.loginUser(loginRequest);

        assertEquals(expectedTokens.getAccessToken(), result.getAccessToken());
        assertEquals(expectedTokens.getRefreshToken(), result.getRefreshToken());
        verify(tokenProvider).getTokenAdminAppAuth();
        verify(keycloakService).loginUser(loginRequest, mockToken);
    }

    @Test
    void shouldThrowExceptionWhenLoginFails() throws KeycloakException {
        String mockToken = "test-token";
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");
        String errorMessage = "Authentication failed";

        when(tokenProvider.getTokenAdminAppAuth()).thenReturn(mockToken);
        when(keycloakService.loginUser(loginRequest, mockToken))
                .thenThrow(new KeycloakException(errorMessage));

        KeycloakException exception = assertThrows(KeycloakException.class, () ->
                authService.loginUser(loginRequest));

        assertEquals(errorMessage, exception.getMessage());
        verify(tokenProvider).getTokenAdminAppAuth();
        verify(keycloakService).loginUser(loginRequest, mockToken);
    }

    @Test
    void shouldGetUserInformationByEmail() throws KeycloakException {
        String email = "test@example.com";
        when(userDataAccessService.getUserInfo(email)).thenReturn(new UserData(
                userEntity.getId(),
                userEntity.getUsername(),
                userEntity.getFirstName(),
                userEntity.getLastName(),
                userEntity.getAddress(),
                userEntity.getEmail(),
                userEntity.getBirthDate(),
                userEntity.getPhoneNumber()
        ));

        UserData result = authService.getUserInformation(email);

        assertNotNull(result);
        assertEquals(userEntity.getId(), result.getIdCard());
        assertEquals(userEntity.getEmail(), result.getEmail());
        verify(userDataAccessService).getUserInfo(email);
    }

    @Test
    void shouldGetUserInformationByUsername() throws KeycloakException {
        String username = "testuser";
        when(userDataAccessService.getUserInfo(username)).thenReturn(new UserData(
                userEntity.getId(),
                userEntity.getUsername(),
                userEntity.getFirstName(),
                userEntity.getLastName(),
                userEntity.getAddress(),
                userEntity.getEmail(),
                userEntity.getBirthDate(),
                userEntity.getPhoneNumber()
        ));

        UserData result = authService.getUserInformation(username);

        assertNotNull(result);
        assertEquals(userEntity.getId(), result.getIdCard());
        assertEquals(userEntity.getUsername(), result.getUsername());
        verify(userDataAccessService).getUserInfo(username);
    }
    @Test
    void shouldReturnNullWhenUserNotFound() throws KeycloakException {
        String username = "nonexistent";
        when(userDataAccessService.getUserInfo(username)).thenReturn(null);

        UserData result = authService.getUserInformation(username);

        assertNull(result);
        verify(userDataAccessService).getUserInfo(username);
    }

    @Test
    void shouldThrowExceptionWhenTokenIsNullDuringRegistration() throws KeycloakException {
        when(tokenProvider.getTokenAdminAppAuth())
                .thenThrow(new KeycloakException("Failed to obtain Keycloak token"));

        KeycloakException exception = assertThrows(KeycloakException.class, () ->
                authService.registerUser(userInformation)
        );

        assertEquals("Failed to obtain Keycloak token", exception.getMessage());
        verify(tokenProvider).getTokenAdminAppAuth();
        verify(userRepository, never()).existsById(any());
    }


}