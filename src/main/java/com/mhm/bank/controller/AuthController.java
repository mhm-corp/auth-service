package com.mhm.bank.controller;

import com.mhm.bank.dto.UserInformation;
import com.mhm.bank.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody UserInformation userInformation) {
        return ResponseEntity.ok(authService.registerUser());
    }

    @GetMapping("/getUser")
    public ResponseEntity<String> getUser() {
        return ResponseEntity.ok("User found");
    }
}
