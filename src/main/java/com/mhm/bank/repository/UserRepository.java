package com.mhm.bank.repository;

import com.mhm.bank.entity.UserEntity;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository  extends ListCrudRepository<UserEntity, String> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
