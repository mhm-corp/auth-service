package com.mhm.bank.service;

import com.mhm.bank.dto.UserInformation;
import com.mhm.bank.dto.UserRegisteredEvent;
import com.mhm.bank.entity.UserEntity;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import com.mhm.bank.service.external.KafkaProducerService;
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
        doesItExist(userInformation);

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
                userEntity.getFirstName(),
                userEntity.getLastName(),
                userEntity.getEmail(),
                userEntity.getAddress(),
                userEntity.getPhoneNumber(),
                userEntity.getBirthDate().toString()
        );
        kafkaProducerService.sendMessage(event);
    }

    private UserEntity sendToDataBase(UserInformation userInformation) {
        UserEntity userEntity = getUserEntity(userInformation);
        userRepository.save(userEntity);
        return userEntity;
    }

    private void doesItExist(UserInformation userInformation) throws UserAlreadyExistsException {
        if (userRepository.existsById(userInformation.idCard())) {
            logger.error("User with ID {} already exists", userInformation.idCard());
            throw new UserAlreadyExistsException("User with ID "+userInformation.idCard()+" already exists");
        }

        if (userRepository.existsByUsername(userInformation.username())) {
            logger.error("Username {} is already taken", userInformation.username());
            throw new UserAlreadyExistsException("Username "+userInformation.username()+" is already taken");
        }

        if (userRepository.existsByEmail(userInformation.email())) {
            logger.error("Email {} is already taken", userInformation.email());
            throw new UserAlreadyExistsException("Email "+userInformation.email()+" is already taken");
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
