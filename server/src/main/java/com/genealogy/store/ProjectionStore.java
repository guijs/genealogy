package com.genealogy.store;

import com.genealogy.domain.projection.ProjectionMarriage;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.projection.ProjectionRelationship;
import com.genealogy.mapper.PersonMapper;
import com.genealogy.mapper.RelationshipMapper;
import com.genealogy.mapper.UnionMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProjectionStore {
    private final PersonMapper personMapper;
    private final UnionMapper unionMapper;
    private final RelationshipMapper relationshipMapper;

    public ProjectionStore(PersonMapper personMapper, UnionMapper unionMapper, RelationshipMapper relationshipMapper) {
        this.personMapper = personMapper;
        this.unionMapper = unionMapper;
        this.relationshipMapper = relationshipMapper;
    }

    public Optional<ProjectionPerson> getPerson(UUID id) {
        ProjectionPerson p = personMapper.findProjectionById(id);
        return p != null ? Optional.of(p.copy()) : Optional.empty();
    }

    public Optional<ProjectionPerson> getEarliestNonHiddenPerson(UUID familyId) {
        ProjectionPerson p = personMapper.findEarliestNonHiddenByFamilyId(familyId);
        return p != null ? Optional.of(p.copy()) : Optional.empty();
    }

    public List<ProjectionPerson> getPersonsByFamily(UUID familyId) {
        return personMapper.findProjectionsByFamilyId(familyId);
    }

    public List<ProjectionMarriage> getMarriagesByFamily(UUID familyId) {
        return unionMapper.findProjectionsByFamilyId(familyId);
    }

    public List<ProjectionRelationship> getRelationshipsByFamily(UUID familyId) {
        return relationshipMapper.findByFamilyId(familyId);
    }

    public List<ProjectionRelationship> getParentsOf(UUID personId) {
        return relationshipMapper.findByChildId(personId);
    }

    public List<ProjectionRelationship> getChildrenOf(UUID personId) {
        return relationshipMapper.findByParentId(personId);
    }

    public List<ProjectionMarriage> getMarriagesOf(UUID personId) {
        return unionMapper.findProjectionsByPartnerId(personId);
    }

    public void createPerson(ProjectionPerson person) {
        ProjectionPerson existing = personMapper.findProjectionById(person.getId());
        if (existing != null) {
            personMapper.update(
                    person.getId(),
                    null,
                    null,
                    person.getDisplayName(),
                    person.getGender() != null ? person.getGender().getValue() : null,
                    person.getBirthYear(),
                    person.getDeathYear(),
                    person.isHidden()
            );
        } else {
            personMapper.insert(
                    person.getId(),
                    person.getFamilyId(),
                    null,
                    null,
                    person.getDisplayName(),
                    person.getGender() != null ? person.getGender().getValue() : null,
                    person.getBirthYear(),
                    person.getDeathYear(),
                    person.isHidden()
            );
        }
    }

    public void upsertPerson(ProjectionPerson person) {
        ProjectionPerson existing = personMapper.findProjectionById(person.getId());
        if (existing != null) {
            personMapper.update(
                    person.getId(),
                    null,
                    null,
                    person.getDisplayName(),
                    person.getGender() != null ? person.getGender().getValue() : null,
                    person.getBirthYear(),
                    person.getDeathYear(),
                    person.isHidden()
            );
        } else {
            createPerson(person);
        }
    }

    public void createMarriage(ProjectionMarriage marriage) {
        ProjectionMarriage existing = unionMapper.findProjectionById(marriage.getId());
        if (existing != null) {
            return;
        }
        unionMapper.insert(
                marriage.getId(),
                marriage.getFamilyId(),
                marriage.getPartner1Id(),
                marriage.getPartner2Id(),
                marriage.getStatus() != null ? marriage.getStatus().getValue() : "active",
                marriage.getStartedAt(),
                marriage.getEndedAt(),
                marriage.getEndedReason()
        );
    }

    public void createRelationship(ProjectionRelationship relationship) {
        ProjectionRelationship existing = relationshipMapper.findById(relationship.getId());
        if (existing != null) {
            return;
        }
        relationshipMapper.insert(
                relationship.getId(),
                relationship.getFamilyId(),
                relationship.getParentId(),
                relationship.getChildId(),
                relationship.getSubtype() != null ? relationship.getSubtype().getValue() : "biological",
                relationship.getRole() != null ? relationship.getRole().getValue() : "parent",
                relationship.getMarriageId(),
                relationship.isDissolved()
        );
    }

    public void updateMarriage(ProjectionMarriage marriage) {
        unionMapper.update(
                marriage.getId(),
                marriage.getStatus() != null ? marriage.getStatus().getValue() : "active",
                marriage.getEndedAt(),
                marriage.getEndedReason()
        );
    }

    public Optional<ProjectionMarriage> getMarriage(UUID id) {
        ProjectionMarriage m = unionMapper.findProjectionById(id);
        return m != null ? Optional.of(m.copy()) : Optional.empty();
    }

    public void clear() {
        relationshipMapper.deleteAll();
        unionMapper.deleteAll();
    }
}
