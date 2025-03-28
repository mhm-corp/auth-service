package com.mhm.bank.service;

import com.mhm.bank.dto.UserInformation;
import com.mhm.bank.dto.UserRegisteredEvent;
import com.mhm.bank.entity.UserEntity;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import com.mhm.bank.service.external.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

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
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);
        doNothing().when(kafkaProducerService).sendMessage(any(UserRegisteredEvent.class));

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

}