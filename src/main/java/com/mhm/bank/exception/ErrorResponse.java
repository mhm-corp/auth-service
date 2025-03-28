package com.mhm.bank.exception;

public record ErrorResponse(
        String errorCode,
        String message
) {}
