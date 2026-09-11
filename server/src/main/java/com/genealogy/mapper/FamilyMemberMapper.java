package com.genealogy.mapper;

import com.genealogy.domain.family.Membership;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface FamilyMemberMapper {
    
    void insert(@Param("familyId") UUID familyId, @Param("userId") UUID userId, @Param("role") String role);
    
    Membership findByFamilyAndUser(@Param("familyId") UUID familyId, @Param("userId") UUID userId);
    
    boolean existsByFamilyAndUser(@Param("familyId") UUID familyId, @Param("userId") UUID userId);
    
    void deleteAll();
}
