package com.mhm.bank.repository;

import com.mhm.bank.entity.UserEntity;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository  extends ListCrudRepository<UserEntity, String> {
}
