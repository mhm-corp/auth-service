package com.mhm.bank.controller.dto;

import java.util.Set;

public record UserKCDto  (
        String username,
        String password,
        String email,
        Set<String> roles
){
}
