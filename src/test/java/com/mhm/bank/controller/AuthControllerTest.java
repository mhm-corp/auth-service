package com.mhm.bank.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mhm.bank.dto.UserInformation;
import com.mhm.bank.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
    }

    @Test
    void registerUser_shouldReturnBadRequest_whenInputIsInvalid() throws Exception {
        UserInformation userInformation = new UserInformation(
                null,
                "",
                "short",
                "John",
                "Doe",
                "123 Main St",
                "invalid-email",
                null,
                "123456789"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userInformation)))
                .andExpect(status().isBadRequest()); // Expect a bad request status
    }
}