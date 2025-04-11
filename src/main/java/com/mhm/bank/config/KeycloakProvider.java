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
    private String serverKeycloakUrl;
    @Value("${keycloak.realm_name}")
    private String realmName;
    @Value("${keycloak.realm.master}")
    private String realmMaster;
    @Value("${keycloak.realm.admin_app}")
    private String adminCli;
    @Value("${keycloak.console.username}")
    private String userConsole;
    @Value("${keycloak.console.password}")
    private String passwordConsole;
    @Value("${keycloak.client.client_secret}")
    private String clientSecret;

    public RealmResource getRealmResouce() {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(serverKeycloakUrl)
                .realm(realmMaster)
                .clientId(adminCli)
                .username(userConsole)
                .password(passwordConsole)
                .clientSecret(clientSecret)
                .resteasyClient(
                        new ResteasyClientBuilderImpl()
                                .connectionPoolSize(10)
                                .build()
                )
                .build();

        return keycloak.realm(realmName);
    }

    public UsersResource getUserResource() {
        return getRealmResouce().users();
    }

}
