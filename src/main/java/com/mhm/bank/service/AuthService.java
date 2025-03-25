package com.mhm.bank.service;

import com.mhm.bank.dto.UserInformation;
import com.mhm.bank.dto.UserRegisteredEvent;
import com.mhm.bank.entity.UserEntity;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final KafkaProducerService kafkaProducerService;

    public AuthService(UserRepository userRepository, KafkaProducerService kafkaProducerService) {
        this.userRepository = userRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Transactional
    public String registerUser(UserInformation userInformation) throws UserAlreadyExistsException {
        doesItExist(userInformation.idCard(), userInformation.username());

        UserEntity userEntity = sendToDataBase(userInformation);
        sendToKafka(userEntity);

        if (logger.isInfoEnabled())
            logger.info(String.format("A new user has been added with the ID %s", userEntity.getId()));

        return String.format("User with ID %s has been added", userEntity.getId());

    }

    private void sendToKafka(UserEntity userEntity) {
        UserRegisteredEvent event = new UserRegisteredEvent(
                userEntity.getId(),
                userEntity.getUsername(),
                userEntity.getEmail()
        );
        kafkaProducerService.sendMessage(event);
    }

    private UserEntity sendToDataBase(UserInformation userInformation) {
        UserEntity userEntity = getUserEntity(userInformation);
        userRepository.save(userEntity);
        return userEntity;
    }

    private void doesItExist(String id, String username) throws UserAlreadyExistsException {
        if (userRepository.existsById(id)) {
            logger.error("User with ID {} already exists", id);
            throw new UserAlreadyExistsException("User with this ID already exists");
        }

        if (userRepository.existsByUsername(username)) {
            logger.error("Username {} is already taken", username);
            throw new UserAlreadyExistsException("Username is already taken");
        }
    }

    private static UserEntity getUserEntity(UserInformation userInformation) {
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

}
