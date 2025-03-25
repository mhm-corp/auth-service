package com.mhm.bank.dto;



import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;

import java.time.LocalDate;


public record UserInformation (
    @Id
    String cedula,
    @NotBlank(message = "Username cannot be empty")
    String username,
    @NotBlank(message = "Password cannot be empty")
    String password,
    @NotBlank(message = "First name cannot be empty")
    String firstName,
    @NotBlank(message = "Last name cannot be empty")
    String lastName,
    String address,
    @Email(message = "Email should be valid")
    String email,
    LocalDate birthdate,
    @Size(max = 20, message = "The phone number is too long.")
    String phoneNumber

) {}
