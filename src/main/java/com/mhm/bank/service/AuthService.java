package com.mhm.bank.service;

import com.mhm.bank.controller.dto.*;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.entity.UserEntity;
import com.mhm.bank.service.external.KafkaProducerService;
import com.mhm.bank.service.external.keycloak.IKeycloakService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    @Value("${kafka.producer.auth.timeout}")
    private int authTimeout;
    @Value("${keycloak.realm.role.user.default}")
    private String kcUserRole;


    private final KafkaProducerService kafkaProducerService;
    private final IKeycloakService keycloakService;
    private final UserDataAccessService userDataAccessService;


    public AuthService(KafkaProducerService kafkaProducerService, IKeycloakService keycloakService, UserDataAccessService userDataAccessService) throws KeycloakException {
        this.kafkaProducerService = kafkaProducerService;
        this.keycloakService = keycloakService;
        this.userDataAccessService = userDataAccessService;
    }

    @Transactional
    public String registerUser(UserInformation userInformation) throws UserAlreadyExistsException, KeycloakException, KafkaException {
        String usernameAfterKC = userInformation.username();
        try {
            String token = keycloakService.getTokenAdminAppAuth();
            userDataAccessService.doesUserExistInDataBase(userInformation);
            sendUserToKeycloak(userInformation, token);
            UserEntity userEntity = userDataAccessService.sendUserToDataBase(userInformation);
            sendEventToKafka(userInformation);

            logger.info("User {} successfully registered with ID: {}", userInformation.username(), userEntity.getId());
            return String.format("User %s with ID %s has been added", userInformation.username(), userEntity.getId());
        } catch (Exception e) {
            if (usernameAfterKC != null) {
                try {
                    keycloakService.deleteUser(usernameAfterKC);
                    logger.info("User {} deleted from Keycloak after transaction failure", usernameAfterKC);
                } catch (Exception ke) {
                    logger.error("Failed to delete user {} from Keycloak after transaction failure", usernameAfterKC, ke);
                }
            }
            throw e;
        }
    }

    private void sendUserToKeycloak(UserInformation userInformation, String token) throws KeycloakException {
        Set<String> roles = (userInformation.roles() != null && !userInformation.roles().isEmpty())
                ? userInformation.roles()
                : Set.of(kcUserRole);

        UserKCDto userKCDto = new UserKCDto(
                userInformation.username(),
                userInformation.password(),
                userInformation.firstName(),
                userInformation.lastName(),
                userInformation.email(),
                roles
        );

        boolean success = keycloakService.createUser(userKCDto, "Bearer " + token);
        if (!success) {
            throw new KeycloakException(String.format("Failed to create user %s in Keycloak", userKCDto.username()));
        }
    }

    private void sendEventToKafka(UserInformation userInformation) throws KafkaException {
        UserRegisteredEvent event = new UserRegisteredEvent(
                userInformation.idCard(),
                userInformation.username(),
                userInformation.firstName(),
                userInformation.lastName(),
                userInformation.email(),
                userInformation.address(),
                userInformation.phoneNumber(),
                userInformation.birthdate().toString()
        );

        try {
            logger.info("Sending message to Kafka for user: {}", userInformation.username());
            kafkaProducerService.sendMessage(event)
                    .thenAccept(result -> logger.info("Message sent successfully to partition {} for user: {}",
                            result.getRecordMetadata().partition(), userInformation.username()))
                    .get(authTimeout, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            logger.error("Failed to send Kafka message - User: {}, Error: {}",
                    userInformation.username(), e.getMessage());
            Thread.currentThread().interrupt();
            throw new KafkaException("Failed to send Kafka message for user: " + userInformation.username(), e);
        }
    }

    public TokensUser loginUser(LoginRequest loginRequest) throws KeycloakException {
        String token = keycloakService.getTokenAdminAppAuth();

        TokensUser tokensUser = keycloakService.loginUser(loginRequest, token);

        logger.info("{}]'s login was successful.", loginRequest.username());
        return tokensUser;
    }

    public UserData getUserInformation(String searchData)  {
        return userDataAccessService.getUserInfo(searchData);
    }

    public TokensUser refreshToken(String refreshToken) throws KeycloakException {
        return keycloakService.getNewToken(refreshToken);
    }

}
