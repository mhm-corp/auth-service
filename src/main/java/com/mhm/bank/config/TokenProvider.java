package com.mhm.bank.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mhm.bank.controller.dto.TokensUser;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.service.dto.TokenResponse;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;

@Component
public class TokenProvider {
    private static final Logger logger = LoggerFactory.getLogger(TokenProvider.class);

    private static final String REFRESH_TOKEN_GRANT = "refresh_token";
    private static final String PASSWORD_GRANT = "password";

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
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwksUrl;
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    private String getTokenUrlFromKeycloak (){
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    private MultiValueMap<String, String> createTokenRequestMap(String grantType, String username, String password) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", grantType);
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);

        if (grantType.equals(PASSWORD_GRANT)) {
            map.add("username", username);
            map.add(PASSWORD_GRANT, password);
            map.add("scope", "openid");
        } else if (grantType.equals(REFRESH_TOKEN_GRANT)) {
            map.add(REFRESH_TOKEN_GRANT, password);
        }

        return map;
    }

    private TokenResponse getTokenFromKeycloak (String username, String password) throws KeycloakException {
        RestTemplate restTemplate = new RestTemplate();
        String tokenUrl = getTokenUrlFromKeycloak();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = createTokenRequestMap(PASSWORD_GRANT, username, password);



        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<TokenResponse> response = null;
        try {
            response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    TokenResponse.class
            );
            return response.getBody();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            handleKeycloakError(e);
            return null;
        }


    }

    private void handleKeycloakError(HttpClientErrorException e) throws KeycloakException {
        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            String errorCode = e.getStatusCode().toString();
            String errorDescription = "Invalid user credentials";
            throw new KeycloakException(errorDescription, errorCode, e);
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode errorResponse = mapper.readTree(e.getResponseBodyAsString());
            String errorCode = errorResponse.get("error").asText();
            String errorDescription = errorResponse.get("error_description").asText();
            throw new KeycloakException(errorDescription, errorCode, e);
        } catch (IOException ex) {
            throw new KeycloakException("Failed to parse error response from Keycloak", e);
        }
    }

    public String getAccessToken() throws KeycloakException {
        TokenResponse body =getTokenFromKeycloak(adminAppName, adminAppPassword);
        if (body == null) {
            throw new KeycloakException("No token response body received from Keycloak");
        }
        return body.getAccessToken();
    }

    public TokensUser getUserAccessToken(String username, String password) throws KeycloakException {
        TokenResponse body = getTokenFromKeycloak(username, password);
        TokensUser tokensUser = new TokensUser();

        if (body != null) {
            tokensUser.setAccessToken(body.getAccessToken());
            tokensUser.setRefreshToken(body.getRefreshToken());
            tokensUser.setExpiresIn(body.getExpiresIn());
        } else {
            throw new KeycloakException("No token response body received from Keycloak");
        }

        return tokensUser;
    }

    public String getTokenAdminAppAuth() throws KeycloakException {
        String token = getAccessToken();
        if (token == null) {
            throw new KeycloakException("Failed to obtain Keycloak token");
        }
        return token;
    }

    public boolean validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            JWKSet publicKeys = JWKSet.load(new URL(jwksUrl));

            String kid = signedJWT.getHeader().getKeyID();
            JWK jwk = publicKeys.getKeyByKeyId(kid);

            if (jwk == null) {
                logger.info("Public key not found for kid: {}", kid);
                return false;
            }

            RSAKey rsaKey = (RSAKey) jwk;
            RSAPublicKey publicKey = rsaKey.toRSAPublicKey();

            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            boolean signatureValid = signedJWT.verify(verifier);

            if (!signatureValid) {
                return false;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            if (!claims.getIssuer().equals(issuer)) {
                return false;
            }

            return !(claims.getExpirationTime() == null || claims.getExpirationTime().before(new java.util.Date()));

        } catch (Exception e) {
            logger.error("Error validating token: ",e);
            return false;
        }
    }

    public TokensUser getNewToken (String refreshToken){
        RestTemplate restTemplate = new RestTemplate();
        String tokenUrl = getTokenUrlFromKeycloak();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = createTokenRequestMap(REFRESH_TOKEN_GRANT, null, refreshToken);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    TokenResponse.class
            );

            TokenResponse body = response.getBody();
            if (body != null) {
                TokensUser tokensUser = new TokensUser();
                tokensUser.setAccessToken(body.getAccessToken());
                tokensUser.setRefreshToken(body.getRefreshToken());
                return tokensUser;
            }
            return null;
        } catch (Exception e) {
            logger.error("Error refreshing token: ", e);
            return null;
        }
    }


}
