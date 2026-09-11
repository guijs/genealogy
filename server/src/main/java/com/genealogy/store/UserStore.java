package com.genealogy.store;

import com.genealogy.domain.user.User;
import com.genealogy.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserStore {
    private final UserMapper userMapper;

    public UserStore(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public void createUser(UUID id, String email, String passwordHash) {
        userMapper.insert(id, email, passwordHash);
    }

    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(userMapper.findById(id));
    }

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(userMapper.findByEmail(email));
    }

    public boolean existsByEmail(String email) {
        return userMapper.existsByEmail(email);
    }

    public void clear() {
        userMapper.deleteAll();
    }
}
