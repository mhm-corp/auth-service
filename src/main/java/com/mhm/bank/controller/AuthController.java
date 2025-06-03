package com.mhm.bank.controller;

import com.mhm.bank.controller.dto.*;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.service.AuthService;
import com.mhm.bank.service.external.keycloak.IKeycloakService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")

@Tag(name = "Authentication and Authorization API", description = "REST API related with user management")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    @Value("${server.at.maximun.expiration.time-sec}")
    private int cookieMaxExpirationTimeSeconds;
    @Value("${cookie.secure}")
    private boolean cookieSecure;
    private final AuthService authService;
    private final IKeycloakService keycloakService;
    private static final String NAME_TOKEN_IN_COOKIE = "accessToken";
    private static final String NAME_REFRESH_TOKEN_IN_COOKIE = "refreshToken";

    public AuthController(AuthService authService, IKeycloakService keycloakService) {
        this.authService = authService;
        this.keycloakService = keycloakService;
    }

    private void putInCookie (String nameCookie, String accessToken, HttpServletResponse response) {
        logger.debug("Setting cookie: {}", nameCookie);
        Cookie cookie = new Cookie(nameCookie, accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(cookieMaxExpirationTimeSeconds);

        response.addCookie(cookie);
        logger.debug("Cookie {} set successfully", nameCookie);
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "User already exists (ID, username or email)"),
            @ApiResponse(responseCode = "500", description = "Internal server error (Keycloak or Kafka errors)")
    })
    public ResponseEntity<String> registerUser(@Valid @RequestBody UserInformation userInformation)
            throws UserAlreadyExistsException, KeycloakException, KafkaException {
        logger.info("Received registration request for user: {}", userInformation.username());
        try {
            String result = authService.registerUser(userInformation);
            logger.info("Successfully registered user: {}", userInformation.username());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            logger.error("Failed to register user: {}. Error: {}", userInformation.username(), e.getMessage());
            throw e;
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Login a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful, tokens set in cookies"),
            @ApiResponse(responseCode = "400", description = "Invalid login request format"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "500", description = "Internal server error (Keycloak errors)")
    })
    public ResponseEntity<Void> loginUser (@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) throws KeycloakException {
        logger.info("Received login request for user: {}", loginRequest.username());
        try {
            TokensUser tokensUser = authService.loginUser(loginRequest);

            if (tokensUser == null)  {
                logger.warn("Login failed for user: {}", loginRequest.username());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            putInCookie(NAME_TOKEN_IN_COOKIE, tokensUser.getAccessToken(), response);
            putInCookie(NAME_REFRESH_TOKEN_IN_COOKIE, tokensUser.getRefreshToken(), response);
            logger.info("Login successful for user: {}", loginRequest.username());
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (Exception e) {
            logger.error("Login error for user: {}. Error: {}", loginRequest.username(), e.getMessage());
            throw e;
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get the logged-in user's information by username or email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid username/email format"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or token expired"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserData> getUserInformation(String username){
        logger.info("Retrieving user information for: {}", username);
        try {
            UserData userInfo = authService.getUserInformation(username);
            if (userInfo != null) {
                logger.info("Successfully retrieved information for user: {}", username);
                return ResponseEntity.ok(userInfo);
            }
            logger.warn("User not found: {}", username);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving user information for: {}. Error: {}", username, e.getMessage());
            throw e;
        }

    }

    @PostMapping("/refresh")
    @Operation(summary = "Use the refresh token when the token has expired")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid token format"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token"),
            @ApiResponse(responseCode = "500", description = "Internal server error (Keycloak errors)")
    })
    public ResponseEntity<Void> refreshTokenResponse(@RequestBody TokenRefreshRequest tokenRequest,
                                                     HttpServletResponse response) throws KeycloakException {
        logger.info("Received token refresh request");
        try {
            String accessToken = tokenRequest.accessToken();
            String refreshToken = tokenRequest.refreshToken();

            if (keycloakService.validateToken(accessToken)) {
                logger.debug("Current token is still valid, no need to refresh");
                return null;
            }

            TokensUser newTokens = authService.refreshToken(refreshToken);
            if (newTokens == null) {
                logger.warn("Token refresh failed - invalid refresh token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            putInCookie(NAME_TOKEN_IN_COOKIE, newTokens.getAccessToken(), response);
            logger.info("Token refresh successful");
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            logger.error("Token refresh failed. Error: {}", e.getMessage());
            throw e;
        }
    }

}
