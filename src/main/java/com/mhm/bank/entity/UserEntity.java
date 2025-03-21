package com.mhm.bank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@NoArgsConstructor
@Getter
@Setter
public class UserEntity {
    @Id
    private String id;
    @Column(name = "username", nullable = false, length = 50)
    private String username;
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;
    private String email;
    private String address;
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    @Column(name = "birth_date")
    private LocalDate birthDate;


}
