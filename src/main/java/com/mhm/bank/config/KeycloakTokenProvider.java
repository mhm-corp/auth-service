package com.mhm.bank.config;

import com.mhm.bank.controller.dto.TokensUser;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.service.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
public class KeycloakTokenProvider {

    @Value("${keycloak.server.url}")
    private String serverUrl;
    @Value("${keycloak.realm_name}")
    private String realm;
    @Value("${jwt.auth.converter.resource-id}")
    private String clientId;
    @Value("${keycloak.client.client_secret}")
    private String clientSecret;
    @Value("${keycloak.user.admin.app.name}")
    private String adminAppName;
    @Value("${keycloak.user.admin.app.password}")
    private String adminAppPassword;

    private String getTokenUrlFromKeycloak (){
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private ResponseEntity<TokenResponse> getTokenFromKeycloak (String username, String password) throws KeycloakException {

        RestTemplate restTemplate = new RestTemplate();
        String tokenUrl = getTokenUrlFromKeycloak();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "password");
        map.add("client_id", clientId);
        map.add("username", username);
        map.add("password", password);
        map.add("client_secret", clientSecret);
        map.add("scope", "openid");


        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<TokenResponse> response = null;
        try {
            response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    TokenResponse.class
            );
            return response;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new KeycloakException("Failed to obtain Keycloak token. Status: " + e.getStatusCode()
                    + ", Response: " + e.getResponseBodyAsString(), e);
        }catch (Exception e) {
            throw new KeycloakException("Failed to connect to Keycloak server: " + e.getMessage(), e);
        }


    }

    public String getAccessToken() throws KeycloakException {
        ResponseEntity<TokenResponse> response  = getTokenFromKeycloak(adminAppName, adminAppPassword);
        return response.getBody() != null ? response.getBody().getAccessToken() : null;

    }

    public TokensUser getUserAccessToken(String username, String password, String token) throws KeycloakException {
        ResponseEntity<TokenResponse> response  = getTokenFromKeycloak(username, password);

        TokensUser tokensUser = new TokensUser();
        if (response.getBody() != null) {
            tokensUser.setAccessToken(response.getBody().getAccessToken());
            tokensUser.setRefreshToken(response.getBody().getRefreshToken());
        }

        return tokensUser;
    }

}
