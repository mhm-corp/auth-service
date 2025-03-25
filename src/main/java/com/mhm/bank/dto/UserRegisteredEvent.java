package com.mhm.bank.dto;

public record UserRegisteredEvent(
        String userId,
        String username,
        String email
) {}
