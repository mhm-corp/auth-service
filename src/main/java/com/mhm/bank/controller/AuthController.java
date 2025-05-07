package com.mhm.bank.controller;

import com.mhm.bank.controller.dto.LoginRequest;
import com.mhm.bank.controller.dto.TokensUser;
import com.mhm.bank.controller.dto.UserData;
import com.mhm.bank.controller.dto.UserInformation;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        Cookie cookie = new Cookie(nameCookie, accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(cookieMaxExpirationTimeSeconds);

        response.addCookie(cookie);
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "409", description = "User already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> registerUser(@Valid @RequestBody UserInformation userInformation)
            throws UserAlreadyExistsException, KeycloakException, KafkaException {

        String result = authService.registerUser(userInformation);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/login")
    @Operation(summary = "Login a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> loginUser (@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) throws KeycloakException {
        TokensUser tokensUser = authService.loginUser(loginRequest);

        if (tokensUser == null)  return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        putInCookie(NAME_TOKEN_IN_COOKIE, tokensUser.getAccessToken(), response);
        putInCookie(NAME_REFRESH_TOKEN_IN_COOKIE, tokensUser.getRefreshToken(), response);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the logged-in user's information by username or email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or token expired"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserData> getUserInformation(
            @CookieValue(value = "accessToken", required = false) String accessToken)  {
        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!keycloakService.validateToken(accessToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        UserData userInfo = authService.getUserInformation(username);
        return userInfo != null ? ResponseEntity.ok(userInfo) : ResponseEntity.notFound().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Use the refresh token when the token has expired")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> refreshTokenResponse(
            @CookieValue(value = "accessToken", required = false) String accessToken,
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) throws KeycloakException {

        if (refreshToken == null || accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (keycloakService.validateToken(accessToken)) {
            logger.debug("Current token is still valid, no need to refresh");
            return null;
        }

        TokensUser newTokens = authService.refreshToken(refreshToken);
        if (newTokens == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        putInCookie(NAME_TOKEN_IN_COOKIE, newTokens.getAccessToken(), response);

        return ResponseEntity.ok().build();
    }

}
