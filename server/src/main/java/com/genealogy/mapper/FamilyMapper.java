package com.genealogy.mapper;

import com.genealogy.domain.family.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface FamilyMapper {
    
    void insert(@Param("id") UUID id, @Param("name") String name);
    
    Family findById(@Param("id") UUID id);
    
    boolean existsById(@Param("id") UUID id);
    
    void deleteAll();
}
