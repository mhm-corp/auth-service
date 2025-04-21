package com.mhm.bank.controller.dto;

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
