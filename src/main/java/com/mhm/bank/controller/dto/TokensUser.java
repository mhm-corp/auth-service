package com.mhm.bank.controller.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokensUser {
    private String accessToken;
    private String refreshToken;
}