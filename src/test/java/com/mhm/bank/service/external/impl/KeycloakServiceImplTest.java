package com.mhm.bank.service.external.impl;

import com.mhm.bank.config.KeycloakProvider;
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
import java.util.List;

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

    @BeforeEach
    void setUp() {
        keycloakService = new KeycloakServiceImpl(keycloakProvider);
        ReflectionTestUtils.setField(keycloakService, "kcUserRole", "user");
    }

    @Test
    void findAllUsers_ShouldReturnUsersList() {
        List<UserRepresentation> expectedUsers = Arrays.asList(new UserRepresentation(), new UserRepresentation());
        when(keycloakProvider.getRealmResouce()).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.list()).thenReturn(expectedUsers);

        List<UserRepresentation> actualUsers = keycloakService.findAllUsers();

        assertEquals(expectedUsers, actualUsers);
        verify(usersResource).list();
    }

    @Test
    void createUser_ShouldCreateUserSuccessfully() throws Exception, KeycloakException {
        UserKCDto userDto = new UserKCDto("testUser", "test@email.com", "password", null);
        String userId = "test-user-id";
        URI location = new URI("/users/" + userId);

        setupMocksForSuccessfulUserCreation(location);

        boolean result = keycloakService.createUser(userDto);

        assertTrue(result);
        verify(usersResource).create(any(UserRepresentation.class));
    }

    @Test
    void createUser_ShouldThrowException_WhenUserExists() {
        UserKCDto userDto = new UserKCDto("existingUser", "test@email.com", "password", null);
        when(keycloakProvider.getUserResource()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(Response.status(409).build());

        assertThrows(KeycloakException.class, () -> keycloakService.createUser(userDto));
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

    private void setupMocksForSuccessfulUserCreation(URI location) throws Exception {
        when(keycloakProvider.getUserResource()).thenReturn(usersResource);
        when(keycloakProvider.getRealmResouce()).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);  // Add this line
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