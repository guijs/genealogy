package com.genealogy.mapper;

import com.genealogy.domain.user.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface UserMapper {

    void insert(@Param("id") UUID id, @Param("email") String email, @Param("passwordHash") String passwordHash);

    User findById(@Param("id") UUID id);

    User findByEmail(@Param("email") String email);

    boolean existsByEmail(@Param("email") String email);

    void deleteAll();
}
