package com.genealogy.support;

import com.genealogy.mapper.*;
import com.genealogy.store.KinshipStore;
import com.genealogy.store.StoryStore;
import com.genealogy.store.UserStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TestDataCleaner {
    private final RelationshipMapper relationshipMapper;
    private final UnionMapper unionMapper;
    private final PersonMapper personMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final FamilyMapper familyMapper;
    private final KinshipStore kinshipStore;
    private final UserStore userStore;
    private final StoryStore storyStore;

    public TestDataCleaner(RelationshipMapper relationshipMapper,
                          UnionMapper unionMapper,
                          PersonMapper personMapper,
                          FamilyMemberMapper familyMemberMapper,
                          FamilyMapper familyMapper,
                          KinshipStore kinshipStore,
                          UserStore userStore,
                          StoryStore storyStore) {
        this.relationshipMapper = relationshipMapper;
        this.unionMapper = unionMapper;
        this.personMapper = personMapper;
        this.familyMemberMapper = familyMemberMapper;
        this.familyMapper = familyMapper;
        this.kinshipStore = kinshipStore;
        this.userStore = userStore;
        this.storyStore = storyStore;
    }

    @Transactional
    public void cleanAll() {
        storyStore.clear();
        relationshipMapper.deleteAll();
        unionMapper.deleteAll();
        personMapper.deleteAll();
        familyMemberMapper.deleteAll();
        familyMapper.deleteAll();
        kinshipStore.clear();
        userStore.clear();
    }
}
