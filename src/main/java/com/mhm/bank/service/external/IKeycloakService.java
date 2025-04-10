package com.mhm.bank.service.external;


import com.mhm.bank.controller.dto.UserKCDto;
import com.mhm.bank.exception.KeycloakException;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

public interface IKeycloakService {

    List<UserRepresentation> findAllUsers();
    boolean createUser(UserKCDto userDto) throws KeycloakException;

}
