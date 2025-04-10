package com.mhm.bank.config;

import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KeycloakProvider {

    @Value("${keycloak.server.url}")
    private static String SERVER_KEYCLOAK_URL;
    @Value("${keycloak.realm_name}")
    private static String REALM_NAME;
    @Value("${keycloak.realm_master}")
    private static String REALM_MASTER;
    @Value("${keycloak.realm.admin_app}")
    private static String ADMIN_CLI;
    @Value("${keycloak.admin.username}")
    private static String USER_CONSOLE = "admin";
    @Value("${keycloak.admin.password}")
    private static String PASSWORD_CONSOLE = "admin";
    @Value("${keycloak.client.client_secret}")
    private static String CLIENT_SECRET;

    public static RealmResource getRealmResouce() {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(SERVER_KEYCLOAK_URL)
                .realm(REALM_MASTER)
                .clientId(ADMIN_CLI)
                .username(USER_CONSOLE)
                .password(PASSWORD_CONSOLE)
                .clientSecret(CLIENT_SECRET)
                .resteasyClient(
                        new ResteasyClientBuilderImpl()
                                .connectionPoolSize(10)
                                .build()
                )
                .build();

        return keycloak.realm(REALM_NAME);
    }

    public static UsersResource getUserResource() {
        return getRealmResouce().users();
    }

}
