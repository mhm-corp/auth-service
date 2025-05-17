package com.mhm.bank.controller.dto;

public record TokenRefreshRequest(String accessToken, String refreshToken) { }