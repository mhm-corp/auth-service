package com.mhm.bank.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mhm.bank.exception.KeycloakException;
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


    public String getAccessToken() throws KeycloakException {
        RestTemplate restTemplate = new RestTemplate();
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "password");
        map.add("client_id", clientId);
        map.add("username", adminAppName);
        map.add("password", adminAppPassword);
        map.add("client_secret", clientSecret);


        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<TokenResponse> response;
        try {
            response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    TokenResponse.class
            );

            return response.getBody() != null ? response.getBody().getAccessToken() : null;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new KeycloakException("Failed to obtain Keycloak token. Status: " + e.getStatusCode()
                    + ", Response: " + e.getResponseBodyAsString(), e);
        }catch (Exception e) {
            throw new KeycloakException("Failed to connect to Keycloak server: " + e.getMessage(), e);
        }

    }

    private static class TokenResponse {
        @JsonProperty("access_token")
        private String access_token;

        public String getAccessToken() {
            return access_token;
        }

        public void setAccessToken(String access_token) {
            this.access_token = access_token;
        }
    }

}
