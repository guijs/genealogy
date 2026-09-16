package com.genealogy.mapper;

import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.ProjectionPerson;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PersonMapper {
    
    void insert(@Param("id") UUID id,
                @Param("familyId") UUID familyId,
                @Param("firstName") String firstName,
                @Param("lastName") String lastName,
                @Param("displayName") String displayName,
                @Param("gender") String gender,
                @Param("birthYear") Integer birthYear,
                @Param("deathYear") Integer deathYear,
                @Param("hidden") boolean hidden);
    
    void update(@Param("id") UUID id,
                @Param("firstName") String firstName,
                @Param("lastName") String lastName,
                @Param("displayName") String displayName,
                @Param("gender") String gender,
                @Param("birthYear") Integer birthYear,
                @Param("deathYear") Integer deathYear,
                @Param("hidden") boolean hidden);
    
    Person findById(@Param("id") UUID id);
    
    ProjectionPerson findProjectionById(@Param("id") UUID id);
    
    List<Person> findByFamilyId(@Param("familyId") UUID familyId);
    
    List<ProjectionPerson> findProjectionsByFamilyId(@Param("familyId") UUID familyId);
    
    boolean existsByIdAndFamilyId(@Param("id") UUID id, @Param("familyId") UUID familyId);
    
    long countByFamilyId(@Param("familyId") UUID familyId);
    
    ProjectionPerson findEarliestNonHiddenByFamilyId(@Param("familyId") UUID familyId);
    
    void deleteById(@Param("id") UUID id);
    
    void deleteAll();
}
