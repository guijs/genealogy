package com.genealogy.mapper;

import com.genealogy.domain.projection.ProjectionRelationship;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface RelationshipMapper {
    
    void insert(@Param("id") UUID id,
                @Param("familyId") UUID familyId,
                @Param("parentId") UUID parentId,
                @Param("childId") UUID childId,
                @Param("subtype") String subtype,
                @Param("role") String role,
                @Param("marriageId") UUID marriageId,
                @Param("dissolved") boolean dissolved);
    
    ProjectionRelationship findById(@Param("id") UUID id);
    
    List<ProjectionRelationship> findByFamilyId(@Param("familyId") UUID familyId);
    
    List<ProjectionRelationship> findByParentId(@Param("parentId") UUID parentId);
    
    List<ProjectionRelationship> findByChildId(@Param("childId") UUID childId);
    
    ProjectionRelationship findByIdAndFamilyId(@Param("id") UUID id, @Param("familyId") UUID familyId);
    
    void setDissolved(@Param("id") UUID id, @Param("dissolved") boolean dissolved);
    
    void deleteAll();
}
