package com.mhm.bank.exception;

public class UserAlreadyExistsException extends Exception {
    public UserAlreadyExistsException(String text) {
        super(text);
    }
}
