package com.mhm.bank.service;

import com.mhm.bank.controller.dto.UserInformation;
import com.mhm.bank.controller.dto.UserKCDto;
import com.mhm.bank.controller.dto.UserRegisteredEvent;
import com.mhm.bank.exception.KeycloakException;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import com.mhm.bank.repository.entity.UserEntity;
import com.mhm.bank.service.external.IKeycloakService;
import com.mhm.bank.service.external.KafkaProducerService;
import org.apache.kafka.common.KafkaException;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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


    private final UserRepository userRepository;
    private final KafkaProducerService kafkaProducerService;
    private final IKeycloakService keycloakService;

    public AuthService(UserRepository userRepository, KafkaProducerService kafkaProducerService, IKeycloakService keycloakService) {
        this.userRepository = userRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.keycloakService = keycloakService;
    }

    @Transactional
    public String registerUser(UserInformation userInformation) throws UserAlreadyExistsException, KeycloakException, KafkaException {
        String usernameAfterKC = null;
        try {
            doesItExistInDataBase(userInformation);
            sendUserToKeycloak(userInformation);
            usernameAfterKC = userInformation.username();
            UserEntity userEntity = sendUserToDataBase(userInformation);
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

    private void sendUserToKeycloak(UserInformation userInformation) throws KeycloakException {
        Set<String> roles = Set.of(kcUserRole);

        UserKCDto userKCDto = new UserKCDto(
                userInformation.username(),
                userInformation.password() ,
                userInformation.email(),
                roles

        );
        boolean success = keycloakService.createUser(userKCDto);
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

    private UserEntity sendUserToDataBase(UserInformation userInformation) {
        UserEntity userEntity = getUserEntity(userInformation);
        userRepository.save(userEntity);
        return userEntity;
    }

    private void doesItExistInDataBase(UserInformation userInformation) throws UserAlreadyExistsException {
        String id = userInformation.idCard();
        if (userRepository.existsById(id)) {
            logger.error("User with ID {} already exists", id);
            throw new UserAlreadyExistsException("User with ID "+id+" already exists");
        }

        String username = userInformation.username();
        if (userRepository.existsByUsername(username)) {
            logger.error("Username {} is already taken", username);
            throw new UserAlreadyExistsException("Username "+username+" is already taken");
        }

        String email = userInformation.email();
        if (userRepository.existsByEmail(email)) {
            logger.error("Email {} is already taken", email);
            throw new UserAlreadyExistsException("Email "+email+" is already taken");
        }
    }

    private UserEntity getUserEntity(UserInformation userInformation) {
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userInformation.idCard());
        userEntity.setUsername(userInformation.username());
        userEntity.setFirstName(userInformation.firstName());
        userEntity.setLastName(userInformation.lastName());
        userEntity.setEmail(userInformation.email());
        userEntity.setAddress(userInformation.address());
        userEntity.setPhoneNumber(userInformation.phoneNumber());
        userEntity.setBirthDate(userInformation.birthdate());
        return userEntity;
    }

    public List<UserRepresentation> findAllUsersByKeycloak() {
        return keycloakService.findAllUsers();
    }

    public String refreshToken(String tokenRefreshRequest) {
        return null;
    }
}
