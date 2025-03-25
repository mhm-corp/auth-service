package com.mhm.bank.service;

import com.mhm.bank.dto.UserInformation;
import com.mhm.bank.entity.UserEntity;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    public String registerUser(UserInformation userInformation) throws UserAlreadyExistsException {
        doesItExist(userInformation.cedula(), userInformation.username());

        UserEntity userEntity = getUserEntity(userInformation);
        userRepository.save(userEntity);

        if (logger.isInfoEnabled())
            logger.info(String.format("A new user has been added with the ID %s", userEntity.getId()));

        return String.format("User with ID %s has been added", userEntity.getId());

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
        userEntity.setId(userInformation.cedula());
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
