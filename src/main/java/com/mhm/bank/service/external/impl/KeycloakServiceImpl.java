package com.mhm.bank.service.external.impl;

import com.mhm.bank.config.KeycloakProvider;
import com.mhm.bank.controller.dto.UserKCDto;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.service.external.IKeycloakService;
import jakarta.ws.rs.core.Response;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeycloakServiceImpl implements IKeycloakService {
    private static final Logger log = LoggerFactory.getLogger(KeycloakServiceImpl.class);
    @Value("${keycloak.realm.role.user.default}")
    private static String KC_USER_ROLE;

    private final static int KC_USER_CREATED_SUCCESFUL = 201;
    private final static int KC_ERROR_USER_EXISTED = 409;


    @Override
    public List<UserRepresentation> findAllUsers() {
        return KeycloakProvider.getRealmResouce().users().list();
    }

    @Override
    public List<UserRepresentation> searchUserByUsername(String username) {
        return KeycloakProvider.getRealmResouce().users().search(username, true);
    }

    @Override
    public boolean createUser(UserKCDto userDto) throws KeycloakException {
        int status  = 0;
        UsersResource usersResource = KeycloakProvider.getUserResource();

        Response response = createNewUser(userDto, usersResource);
        status = response.getStatus();

        if (status == KC_USER_CREATED_SUCCESFUL){
            String path = response.getLocation().getPath();
            String userId = path.substring(path.lastIndexOf("/") + 1);
            setPasswordUser(userDto, usersResource, userId);

            assignRoleToUser(userDto, userId);
            return true;

        } else if (status == KC_ERROR_USER_EXISTED) {
            log.error("User {} already exists", userDto.username());
            throw new KeycloakException("User {"+userDto.username()+"} already exists.");

        } else {
            log.error("Error creating user {}", userDto.username());
            throw new KeycloakException("Error creating user {"+userDto.username()+"}");

        }
    }

    private void assignRoleToUser(UserKCDto userDto, String userId) {

        RealmResource realmResource = KeycloakProvider.getRealmResouce();
        List<RoleRepresentation> roleRepresentations = null;

        if (userDto.roles() == null || userDto.roles().isEmpty()) {
            roleRepresentations = List.of(realmResource.roles().get(KC_USER_ROLE).toRepresentation());
        } else {
            List<String> availableRoles = realmResource.roles().list().stream()
                    .map(RoleRepresentation::getName)
                    .toList();

            List<String> nonExistentRoles = userDto.roles().stream()
                    .filter(role -> !availableRoles.contains(role))
                    .toList();

            if (!nonExistentRoles.isEmpty()) {
                log.warn("The following roles don't exist in Keycloak: {}", nonExistentRoles);
            }

            roleRepresentations = realmResource.roles()
                    .list()
                    .stream()
                    .filter(role -> userDto.roles()
                            .stream()
                            .anyMatch(roleName -> roleName.equalsIgnoreCase(role.getName())))
                    .toList();

            if (roleRepresentations.isEmpty()) {
                log.warn("No valid roles found. Assigning default role.");
                roleRepresentations = List.of(realmResource.roles().get(KC_USER_ROLE).toRepresentation());
            }
        }

        realmResource.users().get(userId)
                .roles()
                .realmLevel()
                .add(roleRepresentations);

    }



    private void setPasswordUser(UserKCDto userDto, UsersResource usersResource, String userId) {
        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setTemporary(false);
        credentialRepresentation.setType(OAuth2Constants.PASSWORD);
        credentialRepresentation.setValue(userDto.password());

        usersResource.get(userId).resetPassword(credentialRepresentation);

    }

    private Response createNewUser(UserKCDto userDto,  UsersResource usersResource){
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(userDto.username());
        userRepresentation.setEmail(userDto.email());
        userRepresentation.setEmailVerified(true);
        userRepresentation.setEnabled(true);

        Response response = usersResource.create(userRepresentation);
        return response;
    }

    @Override
    public void deleteUser(String userId) {

    }

    @Override
    public void updateUser(String userId, UserKCDto userDto) {

    }

}
