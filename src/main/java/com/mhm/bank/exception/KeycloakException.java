package com.mhm.bank.exception;

public class KeycloakException extends Throwable {
    public KeycloakException(String message) {
        super(message);
    }

    public KeycloakException(String message, Exception cause) {
        super(message, cause);
    }
}
