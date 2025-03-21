package com.mhm.bank.service;

import com.mhm.bank.dto.UserInformation;
import com.mhm.bank.entity.UserEntity;
import com.mhm.bank.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class AuthServiceTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:15-alpine"
    ).withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }


    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldRegisterUserSuccessfully() {
        UserInformation userInformation = new UserInformation(
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

        String result = authService.registerUser(userInformation);

        String expectedResult = String.format("User with ID %s has been added", userInformation.id());
        assertEquals(expectedResult, result);


        UserEntity savedUser = userRepository.findById(userInformation.id())
                .orElseThrow(() -> new AssertionError("User not found"));

        assertEquals(userInformation.username(), savedUser.getUsername());
        assertEquals(userInformation.firstName(), savedUser.getFirstName());
        assertEquals(userInformation.lastName(), savedUser.getLastName());
        assertEquals(userInformation.email(), savedUser.getEmail());
        assertEquals(userInformation.address(), savedUser.getAddress());
        assertEquals(userInformation.phoneNumber(), savedUser.getPhoneNumber());
        assertEquals(userInformation.birthDate(), savedUser.getBirthDate());
    }

}