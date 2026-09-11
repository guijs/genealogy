package com.genealogy.service;

import com.genealogy.domain.kinship.Relation;
import com.genealogy.domain.kinship.RelationType;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.ParentChildSubtype;
import com.genealogy.domain.projection.ParentRole;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.projection.ProjectionRelationship;
import com.genealogy.store.KinshipStore;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RelationshipService {
    private final PersonStore personStore;
    private final KinshipStore kinshipStore;
    private final ProjectionStore projectionStore;

    public RelationshipService(PersonStore personStore, KinshipStore kinshipStore, ProjectionStore projectionStore) {
        this.personStore = personStore;
        this.kinshipStore = kinshipStore;
        this.projectionStore = projectionStore;
    }

    @Transactional
    public void addParentChild(UUID familyId, UUID parentId, UUID childId, RelationType relationType) {
        if (!personStore.existsInFamily(parentId, familyId)) {
            throw new PersonNotInFamilyException();
        }
        if (!personStore.existsInFamily(childId, familyId)) {
            throw new PersonNotInFamilyException();
        }

        Relation rel = new Relation(parentId, childId, relationType);
        kinshipStore.addRelation(familyId, rel);

        try {
            syncToProjectionStore(familyId, parentId, childId, relationType);
        } catch (Exception e) {
            kinshipStore.invalidateCache(familyId);
            throw e;
        }
    }

    private void syncToProjectionStore(UUID familyId, UUID parentId, UUID childId, RelationType relationType) {
        Person parent = personStore.getPerson(parentId).orElse(null);
        Person child = personStore.getPerson(childId).orElse(null);
        if (parent == null || child == null) {
            return;
        }

        upsertProjectionPerson(parent);
        upsertProjectionPerson(child);

        ParentChildSubtype subtype = mapSubtype(relationType);
        ParentRole role = mapRole(relationType);

        ProjectionRelationship projRel = new ProjectionRelationship(
                UUID.randomUUID(),
                familyId,
                parentId,
                childId,
                subtype,
                role,
                null,
                false
        );
        projectionStore.createRelationship(projRel);
    }

    private void upsertProjectionPerson(Person person) {
        if (projectionStore.getPerson(person.getId()).isPresent()) {
            return;
        }

        String displayName = buildDisplayName(person.getFirstName(), person.getLastName());
        ProjectionPerson projPerson = new ProjectionPerson(
                person.getId(),
                person.getFamilyId(),
                displayName,
                Gender.UNKNOWN,
                null,
                null,
                false
        );
        projectionStore.createPerson(projPerson);
    }

    private String buildDisplayName(String firstName, String lastName) {
        String first = firstName != null ? firstName : "";
        String last = lastName != null ? lastName : "";
        return (first + " " + last).trim();
    }

    private ParentChildSubtype mapSubtype(RelationType relationType) {
        return switch (relationType) {
            case BIOLOGICAL_FATHER, BIOLOGICAL_MOTHER -> ParentChildSubtype.BIOLOGICAL;
            case ADOPTIVE_FATHER, ADOPTIVE_MOTHER -> ParentChildSubtype.ADOPTIVE;
        };
    }

    private ParentRole mapRole(RelationType relationType) {
        return switch (relationType) {
            case BIOLOGICAL_FATHER, ADOPTIVE_FATHER -> ParentRole.FATHER;
            case BIOLOGICAL_MOTHER, ADOPTIVE_MOTHER -> ParentRole.MOTHER;
        };
    }

    public static class PersonNotInFamilyException extends RuntimeException {
        public PersonNotInFamilyException() {
            super("person does not belong to this family");
        }
    }
}
