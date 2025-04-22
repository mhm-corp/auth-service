package com.mhm.bank.controller.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserData {
    private String idCard;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String address;
    private String email;
    private LocalDate birthdate;
    private String phoneNumber;
    private List<String> roles;

}
