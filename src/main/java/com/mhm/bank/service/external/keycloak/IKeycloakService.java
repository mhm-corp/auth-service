package com.mhm.bank.service.external.keycloak;


import com.mhm.bank.controller.dto.LoginRequest;
import com.mhm.bank.controller.dto.TokensUser;
import com.mhm.bank.controller.dto.UserKCDto;
import com.mhm.bank.exception.KeycloakException;

public interface IKeycloakService {

    boolean createUser(UserKCDto userDto, String authToken) throws KeycloakException;

    void deleteUser(String usernameAfterKC) throws KeycloakException;

    TokensUser loginUser(LoginRequest loginRequest, String token) throws KeycloakException;

}
