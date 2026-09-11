package com.genealogy.store;

import com.genealogy.domain.projection.ProjectionMarriage;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.projection.ProjectionRelationship;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ProjectionStore {
    private final Map<UUID, ProjectionPerson> persons = new ConcurrentHashMap<>();
    private final Map<UUID, ProjectionMarriage> marriages = new ConcurrentHashMap<>();
    private final Map<UUID, ProjectionRelationship> relationships = new ConcurrentHashMap<>();

    public Optional<ProjectionPerson> getPerson(UUID id) {
        ProjectionPerson p = persons.get(id);
        return p != null ? Optional.of(p.copy()) : Optional.empty();
    }

    public List<ProjectionPerson> getPersonsByFamily(UUID familyId) {
        return persons.values().stream()
                .filter(p -> p.getFamilyId().equals(familyId))
                .map(ProjectionPerson::copy)
                .collect(Collectors.toList());
    }

    public List<ProjectionMarriage> getMarriagesByFamily(UUID familyId) {
        return marriages.values().stream()
                .filter(m -> m.getFamilyId().equals(familyId))
                .map(ProjectionMarriage::copy)
                .collect(Collectors.toList());
    }

    public List<ProjectionRelationship> getRelationshipsByFamily(UUID familyId) {
        return relationships.values().stream()
                .filter(r -> r.getFamilyId().equals(familyId))
                .map(ProjectionRelationship::copy)
                .collect(Collectors.toList());
    }

    public List<ProjectionRelationship> getParentsOf(UUID personId) {
        return relationships.values().stream()
                .filter(r -> r.getChildId().equals(personId))
                .map(ProjectionRelationship::copy)
                .collect(Collectors.toList());
    }

    public List<ProjectionRelationship> getChildrenOf(UUID personId) {
        return relationships.values().stream()
                .filter(r -> r.getParentId().equals(personId))
                .map(ProjectionRelationship::copy)
                .collect(Collectors.toList());
    }

    public List<ProjectionMarriage> getMarriagesOf(UUID personId) {
        return marriages.values().stream()
                .filter(m -> m.getPartner1Id().equals(personId) || m.getPartner2Id().equals(personId))
                .map(ProjectionMarriage::copy)
                .collect(Collectors.toList());
    }

    public void createPerson(ProjectionPerson person) {
        persons.put(person.getId(), person);
    }

    public void createMarriage(ProjectionMarriage marriage) {
        marriages.put(marriage.getId(), marriage);
    }

    public void createRelationship(ProjectionRelationship relationship) {
        relationships.put(relationship.getId(), relationship);
    }

    public void clear() {
        persons.clear();
        marriages.clear();
        relationships.clear();
    }
}
