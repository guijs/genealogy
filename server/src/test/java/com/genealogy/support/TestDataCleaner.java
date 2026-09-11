package com.genealogy.support;

import com.genealogy.mapper.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TestDataCleaner {
    private final RelationshipMapper relationshipMapper;
    private final UnionMapper unionMapper;
    private final PersonMapper personMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final FamilyMapper familyMapper;

    public TestDataCleaner(RelationshipMapper relationshipMapper,
                          UnionMapper unionMapper,
                          PersonMapper personMapper,
                          FamilyMemberMapper familyMemberMapper,
                          FamilyMapper familyMapper) {
        this.relationshipMapper = relationshipMapper;
        this.unionMapper = unionMapper;
        this.personMapper = personMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.familyMapper = familyMapper;
    }

    @Transactional
    public void cleanAll() {
        relationshipMapper.deleteAll();
        unionMapper.deleteAll();
        personMapper.deleteAll();
        familyMemberMapper.deleteAll();
        familyMapper.deleteAll();
    }
}
