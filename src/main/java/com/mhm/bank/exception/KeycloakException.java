package com.mhm.bank.exception;

import lombok.Getter;
import org.springframework.web.client.HttpClientErrorException;

@Getter
public class KeycloakException extends Exception {
    private final String errorDescription;
    private final String errorCode;

    public KeycloakException(String errorDescription) {
        super(errorDescription);
        this.errorDescription = errorDescription;
        this.errorCode = null;
    }

    public KeycloakException(String errorDescription, Exception cause) {
        super(errorDescription, cause);
        this.errorDescription = errorDescription;
        this.errorCode = null;
    }

    public KeycloakException(String errorDescription, String errorCode, HttpClientErrorException e) {
        super(errorCode+" "+errorDescription, e);
        this.errorDescription = errorDescription;
        this.errorCode = errorCode;
    }

}
