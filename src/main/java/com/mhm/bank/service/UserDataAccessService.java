package com.mhm.bank.service;

import com.mhm.bank.controller.dto.UserData;
import com.mhm.bank.controller.dto.UserInformation;
import com.mhm.bank.controller.validators.EmailValidator;
import com.mhm.bank.exception.UserAlreadyExistsException;
import com.mhm.bank.repository.UserRepository;
import com.mhm.bank.repository.entity.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserDataAccessService {
    private static final Logger logger = LoggerFactory.getLogger(UserDataAccessService.class);

    private final UserRepository userRepository;

    public UserDataAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void doesUserExistInDataBase(UserInformation userInformation) throws UserAlreadyExistsException {
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

    public UserEntity sendUserToDataBase(UserInformation userInformation) {
        UserEntity userEntity = getUserEntity(userInformation);
        userRepository.save(userEntity);
        return userEntity;
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

    private UserData getUserData(UserEntity userEntity) {
        UserData userdata = new UserData();
        userdata.setIdCard(userEntity.getId());
        userdata.setUsername(userEntity.getUsername());
        userdata.setFirstName(userEntity.getFirstName());
        userdata.setLastName(userEntity.getLastName());
        userdata.setAddress(userEntity.getAddress());
        userdata.setEmail(userEntity.getEmail());
        userdata.setBirthdate(userEntity.getBirthDate());
        userdata.setPhoneNumber(userEntity.getPhoneNumber());
        return userdata;
    }

    public UserData getUserInfo(String searchData)  {
        UserEntity userEntity = EmailValidator.isItAValidEmailFormat(searchData) ? userRepository.findByEmail(searchData) : userRepository.findByUsername(searchData);

        if (userEntity == null) return  null;

        return getUserData(userEntity);
    }

}
