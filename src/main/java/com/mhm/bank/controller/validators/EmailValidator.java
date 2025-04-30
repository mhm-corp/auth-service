package com.mhm.bank.controller.validators;

public class EmailValidator {

    public static boolean isItAValidEmailFormat(String emailStr) {
        return emailStr != null && emailStr.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}
