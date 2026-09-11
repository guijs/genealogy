package com.genealogy.service;

import com.genealogy.domain.kinship.Relation;
import com.genealogy.domain.kinship.RelationType;
import com.genealogy.store.KinshipStore;
import com.genealogy.store.PersonStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RelationshipService {
    private final PersonStore personStore;
    private final KinshipStore kinshipStore;

    public RelationshipService(PersonStore personStore, KinshipStore kinshipStore) {
        this.personStore = personStore;
        this.kinshipStore = kinshipStore;
    }

    public void addParentChild(UUID familyId, UUID parentId, UUID childId, RelationType relationType) {
        if (!personStore.existsInFamily(parentId, familyId)) {
            throw new PersonNotInFamilyException();
        }
        if (!personStore.existsInFamily(childId, familyId)) {
            throw new PersonNotInFamilyException();
        }

        Relation rel = new Relation(parentId, childId, relationType);
        kinshipStore.addRelation(familyId, rel);
    }

    public static class PersonNotInFamilyException extends RuntimeException {
        public PersonNotInFamilyException() {
            super("person does not belong to this family");
        }
    }
}
