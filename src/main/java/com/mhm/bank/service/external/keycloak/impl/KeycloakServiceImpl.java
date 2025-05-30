package com.mhm.bank.service.external.keycloak.impl;

import com.mhm.bank.config.KeycloakProvider;
import com.mhm.bank.config.TokenProvider;
import com.mhm.bank.controller.dto.LoginRequest;
import com.mhm.bank.controller.dto.TokensUser;
import com.mhm.bank.controller.dto.UserKCDto;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.service.external.keycloak.IKeycloakService;
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
    private static final Logger logger = LoggerFactory.getLogger(KeycloakServiceImpl.class);
    @Value("${keycloak.realm.role.user.default}")
    private String kcUserRole;

    private static final int KC_USER_CREATED_SUCCESFUL = 201;
    private static final int KC_ERROR_USER_EXISTED = 409;

    private KeycloakProvider keycloakProvider;
    private TokenProvider tokenProvider;

    public KeycloakServiceImpl(KeycloakProvider keycloakProvider, TokenProvider tokenProvider) {
        this.keycloakProvider = keycloakProvider;
        this.tokenProvider = tokenProvider;
        logger.info("KeycloakServiceImpl initialized");
    }

    @Override
    public boolean createUser(UserKCDto userDto, String authToken) throws KeycloakException {
        logger.info("Creating new user in Keycloak: {}", userDto.username());
        UsersResource usersResource = keycloakProvider.getUserResource();

        Response response = createNewUser(userDto, usersResource);
        int status = response.getStatus();
        logger.debug("User creation response status: {}", status);

        if (status == KC_USER_CREATED_SUCCESFUL){
            String path = response.getLocation().getPath();
            String userId = path.substring(path.lastIndexOf("/") + 1);
            logger.debug("User created with ID: {}", userId);

            setPasswordUser(userDto, usersResource, userId);
            logger.debug("Password set for user: {}", userDto.username());

            assignRoleToUser(userDto, userId);
            logger.info("User {} successfully created with roles", userDto.username());
            return true;

        } else if (status == KC_ERROR_USER_EXISTED) {
            logger.error("User already exists: {}", userDto.username());
            throw new KeycloakException("User already exists: " + userDto.username());
        } else {
            logger.error("Failed to create user: {} with status: {}", userDto.username(), status);
            throw new KeycloakException("Failed to create user: " + userDto.username());
        }
    }

    private void assignRoleToUser(UserKCDto userDto, String userId) {
        logger.debug("Assigning roles to user: {}", userDto.username());
        RealmResource realmResource = keycloakProvider.getRealmResouce();
        List<RoleRepresentation> roleRepresentations = null;

        if (userDto.roles() == null || userDto.roles().isEmpty()) {
            logger.debug("No roles specified, assigning default role: {}", kcUserRole);
            roleRepresentations = List.of(realmResource.roles().get(kcUserRole).toRepresentation());
        } else {
           validateRolesExistKeycloak(realmResource, userDto);

            roleRepresentations = realmResource.roles()
                    .list()
                    .stream()
                    .filter(role -> userDto.roles()
                            .stream()
                            .anyMatch(roleName -> roleName.equalsIgnoreCase(role.getName())))
                    .toList();

            if (roleRepresentations.isEmpty()) {
                logger.warn("No valid roles found for user {}, assigning default role", userDto.username());
                roleRepresentations = List.of(realmResource.roles().get(kcUserRole).toRepresentation());
            }
        }

        realmResource.users().get(userId)
                .roles()
                .realmLevel()
                .add(roleRepresentations);
        logger.info("Roles assigned successfully to user: {}", userDto.username());
    }

    private void validateRolesExistKeycloak (RealmResource realmResource, UserKCDto userDto){
        logger.debug("Validating roles for user: {}", userDto.username());
        List<String> availableRoles = realmResource.roles()
                .list()
                .stream()
                .map(RoleRepresentation::getName)
                .toList();

        List<String> nonExistentRoles = userDto.roles().stream()
                .filter(role -> !availableRoles.contains(role))
                .toList();

        if (!nonExistentRoles.isEmpty()) {
            logger.warn("Non-existent roles requested for user {}: {}", userDto.username(), nonExistentRoles);
        }
    }

    private void setPasswordUser(UserKCDto userDto, UsersResource usersResource, String userId) {
        logger.debug("Setting password for user: {}", userDto.username());
        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setTemporary(false);
        credentialRepresentation.setType(OAuth2Constants.PASSWORD);
        credentialRepresentation.setValue(userDto.password());

        usersResource.get(userId).resetPassword(credentialRepresentation);
        logger.debug("Password set successfully for user: {}", userDto.username());
    }

    private Response createNewUser(UserKCDto userDto,  UsersResource usersResource){
        logger.debug("Creating user representation for: {}", userDto.username());
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(userDto.username());
        userRepresentation.setFirstName(userDto.firstName());
        userRepresentation.setLastName(userDto.lastName());
        userRepresentation.setEmail(userDto.email());
        userRepresentation.setEmailVerified(true);
        userRepresentation.setEnabled(true);

        return usersResource.create(userRepresentation);
    }

    @Override
    public void deleteUser(String usernameAfterKC) throws KeycloakException {
        logger.debug("Deleting user {}", usernameAfterKC);
        try {
            UsersResource usersResource = keycloakProvider.getUserResource();
            List<UserRepresentation> users = usersResource.searchByUsername(usernameAfterKC, true);

            if (!users.isEmpty()) {
                String userId = users.get(0).getId();
                usersResource.get(userId).remove();
                logger.info("User {} successfully deleted from Keycloak", usernameAfterKC);
            } else {
                logger.warn("User {} not found in Keycloak", usernameAfterKC);
            }
        } catch (Exception e) {
            logger.error("Error deleting user {} from Keycloak: {}", usernameAfterKC, e.getMessage());
            throw new KeycloakException("Error deleting user from Keycloak: " + e.getMessage());
        }
    }

    @Override
    public TokensUser loginUser(LoginRequest loginRequest, String token) throws KeycloakException {
        logger.info("Processing login request for user: {}", loginRequest.username());
        try {
            TokensUser tokens = tokenProvider.getUserAccessToken(loginRequest.username(), loginRequest.password());
            logger.info("Login successful for user: {}", loginRequest.username());
            return tokens;
        } catch (Exception e) {
            logger.error("Login failed for user: {}. Error: {}", loginRequest.username(), e.getMessage());
            throw e;
        }
    }

    @Override
    public String getTokenAdminAppAuth () throws KeycloakException {
        logger.debug("Requesting admin app authentication token");
        try {
            String token = tokenProvider.getTokenAdminAppAuth();
            logger.debug("Admin app token obtained successfully");
            return token;
        } catch (Exception e) {
            logger.error("Failed to get admin app token: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public TokensUser getNewToken(String refreshToken) {
        logger.debug("Requesting new token using refresh token");
        try {
            TokensUser tokens = tokenProvider.getNewToken(refreshToken);
            logger.debug("Token refresh successful");
            return tokens;
        } catch (Exception e) {
            logger.error("Token refresh failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean validateToken(String token) {
        logger.debug("Validating token");
        boolean isValid = tokenProvider.validateToken(token);
        logger.debug("Token validation result: {}", isValid);
        return isValid;
    }

}
