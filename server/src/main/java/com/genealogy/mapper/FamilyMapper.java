package com.genealogy.mapper;

import com.genealogy.domain.family.Family;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface FamilyMapper {
    
    void insert(@Param("id") UUID id, @Param("name") String name);
    
    Family findById(@Param("id") UUID id);
    
    boolean existsById(@Param("id") UUID id);
    
    void updateProgenitor(@Param("id") UUID id, @Param("progenitorPersonId") UUID progenitorPersonId);
    
    void updateGenerationNames(@Param("id") UUID id, 
                               @Param("generationNames") List<String> generationNames,
                               @Param("generationNameAlign") String generationNameAlign);
    
    void deleteAll();
}
