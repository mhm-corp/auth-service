package com.mhm.bank.config;

import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KeycloakProvider {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakProvider.class);
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
        logger.debug("Initializing Keycloak client for realm: {}", realmName);
        try {
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
            logger.info("Successfully initialized Keycloak client for realm: {}", realmName);
            return keycloak.realm(realmName);
        } catch (Exception e) {
            logger.error("Failed to initialize Keycloak client for realm: {}. Error: {}", realmName, e.getMessage(), e);
            throw e;
        }
    }

    public UsersResource getUserResource() {
        logger.debug("Retrieving users resource for realm: {}", realmName);
        try {
            UsersResource usersResource = getRealmResouce().users();
            logger.debug("Successfully retrieved users resource for realm: {}", realmName);
            return usersResource;
        } catch (Exception e) {
            logger.error("Failed to retrieve users resource for realm: {}. Error: {}", realmName, e.getMessage(), e);
            throw e;
        }
    }

}
