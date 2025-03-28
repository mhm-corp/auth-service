package com.mhm.bank.dto;

public record UserRegisteredEvent(
        String userId,
        String username,
        String firstName,
        String lastName,
        String email,
        String address,
        String phoneNumber,
        String birthDate

) {}
