package com.mhm.bank.controller.dto;

import lombok.Builder;

import java.util.Set;

@Builder
public record UserKCDto  (
        String username,
        String password,
        String email,
        Set<String> roles
){
}
