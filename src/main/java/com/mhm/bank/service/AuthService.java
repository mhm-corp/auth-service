package com.mhm.bank.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);


    public String registerUser() {
        logger.info("*************Registering a new user.");
        return "Registered";
    }

}
