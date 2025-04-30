package com.mhm.bank.controller.dto;

public record LoginRequest(
        String username,
        String password
) {
}
