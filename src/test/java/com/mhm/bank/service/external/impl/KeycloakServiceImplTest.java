package com.mhm.bank.service.external.impl;

import com.mhm.bank.config.KeycloakProvider;
import com.mhm.bank.config.KeycloakTokenProvider;
import com.mhm.bank.controller.dto.LoginRequest;
import com.mhm.bank.controller.dto.TokensUser;
import com.mhm.bank.controller.dto.UserKCDto;
import com.mhm.bank.exception.KeycloakException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakServiceImplTest {

    @Mock
    private KeycloakProvider keycloakProvider;
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private RolesResource rolesResource;
    @Mock
    private Response response;
    @Mock
    private UserResource userResource;
    @Mock
    private RoleMappingResource roleMappingResource;
    @Mock
    private RoleScopeResource roleScopeResource;

    private KeycloakServiceImpl keycloakService;
    @Mock
    private KeycloakTokenProvider keycloakTokenProvider;

    @BeforeEach
    void setUp() {
        keycloakService = new KeycloakServiceImpl(keycloakProvider, keycloakTokenProvider);
        ReflectionTestUtils.setField(keycloakService, "kcUserRole", "user");
    }

    @Test
    void createUser_ShouldCreateUserSuccessfully() throws Exception, KeycloakException {
        UserKCDto userDto = new UserKCDto("testUser",  "password", "fname","lname","test@email.com",null);

        String authToken = "test-token";
        String userId = "test-user-id";
        URI location = new URI("/users/" + userId);

        setupMocksForSuccessfulUserCreation(location);

        boolean result = keycloakService.createUser(userDto, authToken);

        assertTrue(result);
        verify(usersResource).create(any(UserRepresentation.class));
    }

    @Test
    void createUser_ShouldThrowException_WhenUserExists() {
        UserKCDto userDto = new UserKCDto("testUser",  "password", "fname","lname","test@email.com",null);

        String authToken = "test-token";
        when(keycloakProvider.getUserResource()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(Response.status(409).build());

        assertThrows(KeycloakException.class, () -> keycloakService.createUser(userDto, authToken));
    }

    @Test
    void deleteUser_ShouldDeleteUserSuccessfully() throws KeycloakException {
        String username = "testUser";
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setId("userId");

        when(keycloakProvider.getUserResource()).thenReturn(usersResource);
        when(usersResource.searchByUsername(username, true))
                .thenReturn(Collections.singletonList(userRepresentation));
        when(usersResource.get(anyString())).thenReturn(userResource);

        keycloakService.deleteUser(username);

        verify(userResource).remove();
    }

    @Test
    void deleteUser_ShouldThrowException_WhenUserNotFound() {
        String username = "nonExistentUser";
        when(keycloakProvider.getUserResource()).thenReturn(usersResource);
        when(usersResource.searchByUsername(username, true)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> keycloakService.deleteUser(username));
    }

    @Test
    void loginUser_ShouldReturnTokensSuccessfully() throws KeycloakException {
        LoginRequest loginRequest = new LoginRequest("testUser", "password");
        String token = "test-token";
        TokensUser expectedTokens = new TokensUser("access-token-123", "refresh-token-456");

        when(keycloakTokenProvider.getUserAccessToken(
                loginRequest.username(),
                loginRequest.password(),
                token
        )).thenReturn(expectedTokens);

        TokensUser result = keycloakService.loginUser(loginRequest, token);

        assertNotNull(result);
        assertEquals(expectedTokens.getAccessToken(), result.getAccessToken());
        assertEquals(expectedTokens.getRefreshToken(), result.getRefreshToken());
        verify(keycloakTokenProvider).getUserAccessToken(
                loginRequest.username(),
                loginRequest.password(),
                token
        );
    }

    @Test
    void loginUser_ShouldThrowException_WhenKeycloakFails() throws KeycloakException {
        LoginRequest loginRequest = new LoginRequest("testUser", "password");
        String token = "test-token";
        String errorMessage = "Authentication failed";

        when(keycloakTokenProvider.getUserAccessToken(
                loginRequest.username(),
                loginRequest.password(),
                token
        )).thenThrow(new KeycloakException(errorMessage));

        KeycloakException exception = assertThrows(KeycloakException.class,
                () -> keycloakService.loginUser(loginRequest, token));
        assertEquals(errorMessage, exception.getMessage());
    }

    @Test
    void createUser_ShouldAssignDefaultRole_WhenInvalidRolesProvided() throws Exception, KeycloakException {
        Set<String> invalidRoles = new HashSet<>(Arrays.asList("invalid_role1", "invalid_role2"));
        UserKCDto userDto = new UserKCDto("testUser", "password", "fname", "lname", "test@email.com", invalidRoles);
        String authToken = "test-token";
        URI location = new URI("/users/test-user-id");

        setupMocksForSuccessfulUserCreation(location);
        when(rolesResource.list()).thenReturn(Collections.emptyList());

        RoleRepresentation defaultRole = new RoleRepresentation();
        defaultRole.setName("user");
        when(rolesResource.get("user").toRepresentation()).thenReturn(defaultRole);

        boolean result = keycloakService.createUser(userDto, authToken);

        assertTrue(result);
        verify(roleScopeResource).add(argThat(roles ->
                roles.size() == 1 && roles.get(0).getName().equals("user")));
    }

    @Test
    void deleteUser_ShouldThrowException_WhenKeycloakFails() {
        String username = "testUser";
        UserRepresentation userRep = new UserRepresentation();
        userRep.setId("userId");

        when(keycloakProvider.getUserResource()).thenReturn(usersResource);
        when(usersResource.searchByUsername(username, true))
                .thenReturn(Collections.singletonList(userRep));
        when(usersResource.get(anyString())).thenReturn(userResource);
        doThrow(new RuntimeException("Keycloak error")).when(userResource).remove();

        assertThrows(KeycloakException.class, () -> keycloakService.deleteUser(username));
    }


    private void setupMocksForSuccessfulUserCreation(URI location)  {
        when(keycloakProvider.getUserResource()).thenReturn(usersResource);
        when(keycloakProvider.getRealmResouce()).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(location);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get(anyString())).thenReturn(mock(RoleResource.class));
        when(rolesResource.get(anyString()).toRepresentation()).thenReturn(new RoleRepresentation());
        when(usersResource.get(anyString())).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
    }

}