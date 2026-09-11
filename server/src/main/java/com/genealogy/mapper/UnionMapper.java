package com.genealogy.mapper;

import com.genealogy.domain.projection.ProjectionMarriage;
import com.genealogy.domain.union.Union;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface UnionMapper {
    
    void insert(@Param("id") UUID id,
                @Param("familyId") UUID familyId,
                @Param("partnerAId") UUID partnerAId,
                @Param("partnerBId") UUID partnerBId,
                @Param("status") String status,
                @Param("startedAt") String startedAt,
                @Param("endedAt") String endedAt,
                @Param("endedReason") String endedReason);
    
    void update(@Param("id") UUID id,
                @Param("status") String status,
                @Param("endedAt") String endedAt,
                @Param("endedReason") String endedReason);
    
    Union findById(@Param("id") UUID id);
    
    Union findByIdAndFamilyId(@Param("id") UUID id, @Param("familyId") UUID familyId);
    
    ProjectionMarriage findProjectionById(@Param("id") UUID id);
    
    List<Union> findByFamilyId(@Param("familyId") UUID familyId);
    
    List<ProjectionMarriage> findProjectionsByFamilyId(@Param("familyId") UUID familyId);
    
    List<Union> findActiveByPartnerId(@Param("partnerId") UUID partnerId);
    
    List<ProjectionMarriage> findProjectionsByPartnerId(@Param("partnerId") UUID partnerId);
    
    boolean hasActiveUnion(@Param("partnerId") UUID partnerId);
    
    void deleteAll();
}
