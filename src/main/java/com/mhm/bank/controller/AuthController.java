package com.mhm.bank.controller;

import com.mhm.bank.controller.dto.LoginRequest;
import com.mhm.bank.controller.dto.TokensUser;
import com.mhm.bank.controller.dto.UserData;
import com.mhm.bank.controller.dto.UserInformation;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")

@Tag(name = "Authentication and Authorization API", description = "REST API related with user management")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    @Value("${server.at.maximun.expiration.time-seg}")
    private int cookieMaxExpirationTimeSegonds;
    @Value("${cookie.secure}")
    private boolean cookieSecure;
    private final AuthService authService;
    private static final String NAME_TOKEN_IN_COOKIE = "accessToken";

    public AuthController(AuthService authService) {
        this.authService = authService;
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

        if (tokensUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Cookie cookie = new Cookie(NAME_TOKEN_IN_COOKIE, tokensUser.getAccessToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(cookieMaxExpirationTimeSegonds);

        response.addCookie(cookie);

        return ResponseEntity.status(HttpStatus.OK).build();
    }
/*
    @GetMapping("/me")
    @PreAuthorize("hasRole('admin_client_role')")
    @Operation(summary = "Get user information by username or email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserData> getUserInformation(
            @RequestParam("searchData") String searchData,
            HttpServletRequest request)  {

        String accessToken = getTokenFromCookie(request);
        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserData userInfo = authService.getUserInformation(searchData);
        return userInfo != null ? ResponseEntity.ok(userInfo) : ResponseEntity.notFound().build();
    }

    private String getTokenFromCookie (HttpServletRequest request){
        Cookie[] cookies = request.getCookies();
        String accessToken = null;

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (NAME_TOKEN_IN_COOKIE.equals(cookie.getName())) {
                    accessToken = cookie.getValue();
                    break;
                }
            }
        }

        return accessToken;
    }

 */
}
