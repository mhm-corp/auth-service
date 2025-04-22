package com.mhm.bank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mhm.bank.controller.dto.LoginRequest;
import com.mhm.bank.controller.dto.TokensUser;
import com.mhm.bank.controller.dto.UserInformation;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;


    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void registerUser_shouldReturnCreated_whenUserRegistrationIsSuccessful() throws Exception, KeycloakException {
        UserInformation userInfo = new UserInformation(
                "12345678",
                "testuser",
                "Password123!",
                "John",
                "Doe",
                "123 Main St",
                "john@example.com",
                LocalDate.of(1990, 1, 1),
                "123456789"
        );

        when(authService.registerUser(any(UserInformation.class)))
                .thenReturn("User with ID 12345678 has been added");

        ResponseEntity<String> response = authController.registerUser(userInfo);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("User with ID 12345678 has been added", response.getBody());
        verify(authService).registerUser(userInfo);
    }

    @Test
    void registerUser_shouldThrowException_whenUserAlreadyExists() throws Exception, KeycloakException {
        UserInformation userInfo = new UserInformation(
                "12345678",
                "testuser",
                "Password123!",
                "John",
                "Doe",
                "123 Main St",
                "john@example.com",
                LocalDate.of(1990, 1, 1),
                "123456789"
        );

        when(authService.registerUser(any(UserInformation.class)))
                .thenThrow(new UserAlreadyExistsException("User already exists"));

        assertThrows(UserAlreadyExistsException.class, () -> authController.registerUser(userInfo));
        verify(authService).registerUser(userInfo);
    }

    @Test
    void registerUser_shouldThrowException_whenEmailIsInvalid() throws Exception, KeycloakException {
        UserInformation userInfo = new UserInformation(
                "12345678",
                "testuser",
                "Password123!",
                "John",
                "Doe",
                "123 Main St",
                "invalid.email",
                LocalDate.of(1990, 1, 1),
                "123456789"
        );

        when(authService.registerUser(any(UserInformation.class)))
                .thenThrow(new IllegalArgumentException("Invalid email format"));

        assertThrows(IllegalArgumentException.class, () -> authController.registerUser(userInfo));
        verify(authService).registerUser(userInfo);
    }

    @Test
    void registerUser_shouldThrowException_whenUserIsUnderAge() throws Exception, KeycloakException {
        UserInformation userInfo = new UserInformation(
                "12345678",
                "testuser",
                "Password123!",
                "John",
                "Doe",
                "123 Main St",
                "john@example.com",
                LocalDate.now().minusYears(17),
                "123456789"
        );

        when(authService.registerUser(any(UserInformation.class)))
                .thenThrow(new IllegalArgumentException("User must be at least 18 years old"));

        assertThrows(IllegalArgumentException.class, () -> authController.registerUser(userInfo));
        verify(authService).registerUser(userInfo);
    }

    @Test
    void registerUser_shouldThrowException_whenIdExists() throws Exception, KeycloakException {
        UserInformation userInfo = new UserInformation(
                "12345678",
                "newusername",
                "Password123!",
                "Jane",
                "Smith",
                "456 Oak St",
                "jane@example.com",
                LocalDate.of(1992, 1, 1),
                "987654321"
        );

        when(authService.registerUser(any(UserInformation.class)))
                .thenThrow(new UserAlreadyExistsException("User ID already registered"));

        assertThrows(UserAlreadyExistsException.class, () -> authController.registerUser(userInfo));
        verify(authService).registerUser(userInfo);
    }

    @Test
    void registerUser_shouldThrowException_whenUsernameExistsWithDifferentData() throws Exception, KeycloakException {
        UserInformation userInfo = new UserInformation(
                "87654321",
                "testuser",
                "Password123!",
                "Jane",
                "Smith",
                "456 Oak St",
                "jane@example.com",
                LocalDate.of(1992, 1, 1),
                "987654321"
        );

        when(authService.registerUser(any(UserInformation.class)))
                .thenThrow(new UserAlreadyExistsException("Username already taken"));

        assertThrows(UserAlreadyExistsException.class, () -> authController.registerUser(userInfo));
        verify(authService).registerUser(userInfo);
    }

    @Test
    void registerUser_shouldThrowException_whenKafkaErrorOccurs() throws Exception, KeycloakException {
        UserInformation userInfo = new UserInformation(
                "12345678",
                "testuser",
                "Password123!",
                "John",
                "Doe",
                "123 Main St",
                "john@example.com",
                LocalDate.of(1990, 1, 1),
                "123456789"
        );

        when(authService.registerUser(any(UserInformation.class)))
                .thenThrow(new KafkaException("Failed to process message"));

        assertThrows(KafkaException.class, () -> authController.registerUser(userInfo));
        verify(authService).registerUser(userInfo);
    }

    @Test
    void loginUser_shouldReturnCreated_whenLoginIsSuccessful() throws KeycloakException {
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");
        TokensUser expectedTokens = new TokensUser("access-token-123", "refresh-token-456");

        when(authService.loginUser(loginRequest)).thenReturn(expectedTokens);

        ResponseEntity<TokensUser> response = authController.loginUser(loginRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expectedTokens, response.getBody());
        verify(authService).loginUser(loginRequest);
    }

    @Test
    void loginUser_shouldThrowException_whenKeycloakFails() throws KeycloakException {
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");

        when(authService.loginUser(loginRequest))
                .thenThrow(new KeycloakException("Authentication failed"));

        assertThrows(KeycloakException.class, () -> authController.loginUser(loginRequest));
        verify(authService).loginUser(loginRequest);
    }

    @Test
    void findAllUsersByKeycloak_shouldReturnUserList() {
        List<UserRepresentation> expectedUsers = Arrays.asList(
                new UserRepresentation(),
                new UserRepresentation()
        );

        when(authService.findAllUsersByKeycloak()).thenReturn(expectedUsers);

        ResponseEntity<?> response = authController.findAllUsersByKeycloak();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedUsers, response.getBody());
        verify(authService).findAllUsersByKeycloak();
    }

}