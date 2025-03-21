package com.mhm.bank.dto;



import org.springframework.data.annotation.Id;

import java.time.LocalDate;


public record UserInformation (
    @Id
    String id,
    String username,
    String password,
    String firstName,
    String lastName,
    String address,
    String email,
    LocalDate birthDate,
    String phoneNumber

) {}
