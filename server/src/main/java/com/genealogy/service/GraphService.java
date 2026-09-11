package com.genealogy.service;

import com.genealogy.domain.projection.*;
import com.genealogy.store.ProjectionStore;
import com.genealogy.web.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GraphService {
    public static final int DEFAULT_DEPTH = 3;
    public static final int MAX_DEPTH = 8;
    private static final String TRUNCATE_REASON = "已达展开上限";

    private final ProjectionStore projectionStore;

    public GraphService(ProjectionStore projectionStore) {
        this.projectionStore = projectionStore;
    }

    public static class PersonNotInFamilyException extends RuntimeException {
        public PersonNotInFamilyException() {
            super("person not in family");
        }
    }

    public GraphProjectionResponse getGraph(UUID familyId, UUID rootPersonId, int depth) {
        if (depth <= 0) {
            depth = DEFAULT_DEPTH;
        }

        Optional<ProjectionPerson> rootOpt = projectionStore.getPerson(rootPersonId);
        if (rootOpt.isEmpty() || !rootOpt.get().getFamilyId().equals(familyId)) {
            throw new PersonNotInFamilyException();
        }

        Set<UUID> visited = new HashSet<>();
        Map<UUID, ProjectionPerson> persons = new HashMap<>();
        Map<UUID, ProjectionMarriage> marriages = new HashMap<>();
        Map<UUID, ProjectionRelationship> relationships = new HashMap<>();
        AtomicBoolean truncated = new AtomicBoolean(false);

        traverse(rootPersonId, familyId, depth, 0, visited, persons, marriages, relationships, truncated);

        filterDanglingRelationships(relationships, persons);
        filterDanglingMarriages(marriages, persons);

        List<PersonDTO> personDTOs = buildPersonDTOs(persons);
        List<MarriageDTO> marriageDTOs = buildMarriageDTOs(marriages, persons);
        List<RelationshipDTO> relationshipDTOs = buildRelationshipDTOs(relationships);

        String truncateReason = truncated.get() ? TRUNCATE_REASON : null;

        return new GraphProjectionResponse(
                familyId.toString(),
                rootPersonId.toString(),
                depth,
                truncated.get(),
                truncateReason,
                personDTOs,
                marriageDTOs,
                relationshipDTOs
        );
    }

    private void traverse(UUID personId, UUID familyId, int maxDepth, int currentDepth,
                          Set<UUID> visited, Map<UUID, ProjectionPerson> persons,
                          Map<UUID, ProjectionMarriage> marriages,
                          Map<UUID, ProjectionRelationship> relationships,
                          AtomicBoolean truncated) {
        if (currentDepth > maxDepth) {
            return;
        }
        if (visited.contains(personId)) {
            return;
        }
        visited.add(personId);

        Optional<ProjectionPerson> personOpt = projectionStore.getPerson(personId);
        if (personOpt.isEmpty() || !personOpt.get().getFamilyId().equals(familyId)) {
            return;
        }
        persons.put(personId, personOpt.get());

        List<ProjectionMarriage> personMarriages = projectionStore.getMarriagesOf(personId);
        for (ProjectionMarriage m : personMarriages) {
            if (!m.getFamilyId().equals(familyId)) {
                continue;
            }
            marriages.put(m.getId(), m);

            UUID spouseId = m.getPartner1Id().equals(personId) ? m.getPartner2Id() : m.getPartner1Id();
            if (!visited.contains(spouseId)) {
                Optional<ProjectionPerson> spouseOpt = projectionStore.getPerson(spouseId);
                if (spouseOpt.isPresent() && spouseOpt.get().getFamilyId().equals(familyId)) {
                    persons.put(spouseId, spouseOpt.get());
                    visited.add(spouseId);
                }
            }
        }

        List<ProjectionRelationship> parentRels = projectionStore.getParentsOf(personId);
        for (ProjectionRelationship r : parentRels) {
            if (!r.getFamilyId().equals(familyId) || r.isDissolved()) {
                continue;
            }
            relationships.put(r.getId(), r);
            if (currentDepth + 1 > maxDepth && !visited.contains(r.getParentId())) {
                truncated.set(true);
            } else {
                traverse(r.getParentId(), familyId, maxDepth, currentDepth + 1, visited, persons, marriages, relationships, truncated);
            }
        }

        List<ProjectionRelationship> childRels = projectionStore.getChildrenOf(personId);
        for (ProjectionRelationship r : childRels) {
            if (!r.getFamilyId().equals(familyId) || r.isDissolved()) {
                continue;
            }
            relationships.put(r.getId(), r);
            if (currentDepth + 1 > maxDepth && !visited.contains(r.getChildId())) {
                truncated.set(true);
            } else {
                traverse(r.getChildId(), familyId, maxDepth, currentDepth + 1, visited, persons, marriages, relationships, truncated);
            }
        }
    }

    private void filterDanglingRelationships(Map<UUID, ProjectionRelationship> relationships,
                                              Map<UUID, ProjectionPerson> persons) {
        Iterator<Map.Entry<UUID, ProjectionRelationship>> it = relationships.entrySet().iterator();
        while (it.hasNext()) {
            ProjectionRelationship r = it.next().getValue();
            if (!persons.containsKey(r.getParentId())) {
                it.remove();
                continue;
            }
            if (!persons.containsKey(r.getChildId())) {
                it.remove();
            }
        }
    }

    private void filterDanglingMarriages(Map<UUID, ProjectionMarriage> marriages,
                                         Map<UUID, ProjectionPerson> persons) {
        Iterator<Map.Entry<UUID, ProjectionMarriage>> it = marriages.entrySet().iterator();
        while (it.hasNext()) {
            ProjectionMarriage m = it.next().getValue();
            if (!persons.containsKey(m.getPartner1Id())) {
                it.remove();
                continue;
            }
            if (!persons.containsKey(m.getPartner2Id())) {
                it.remove();
            }
        }
    }

    private List<PersonDTO> buildPersonDTOs(Map<UUID, ProjectionPerson> persons) {
        List<PersonDTO> dtos = new ArrayList<>();
        for (ProjectionPerson p : persons.values()) {
            if (p.isHidden()) {
                continue;
            }
            dtos.add(new PersonDTO(
                    p.getId().toString(),
                    p.getDisplayName(),
                    p.getGender(),
                    p.getBirthYear(),
                    p.getDeathYear(),
                    p.getDeathYear() != null
            ));
        }
        return dtos;
    }

    private List<MarriageDTO> buildMarriageDTOs(Map<UUID, ProjectionMarriage> marriages,
                                                 Map<UUID, ProjectionPerson> persons) {
        List<MarriageDTO> dtos = new ArrayList<>();
        for (ProjectionMarriage m : marriages.values()) {
            ProjectionPerson partner1 = persons.get(m.getPartner1Id());
            ProjectionPerson partner2 = persons.get(m.getPartner2Id());
            if (partner1 != null && partner1.isHidden()) {
                continue;
            }
            if (partner2 != null && partner2.isHidden()) {
                continue;
            }
            dtos.add(new MarriageDTO(
                    m.getId().toString(),
                    new String[]{m.getPartner1Id().toString(), m.getPartner2Id().toString()},
                    m.getStatus(),
                    m.getStartedAt(),
                    m.getEndedAt(),
                    m.getEndedReason()
            ));
        }
        return dtos;
    }

    private List<RelationshipDTO> buildRelationshipDTOs(Map<UUID, ProjectionRelationship> relationships) {
        List<RelationshipDTO> dtos = new ArrayList<>();
        for (ProjectionRelationship r : relationships.values()) {
            if (r.isDissolved()) {
                continue;
            }
            Optional<ProjectionPerson> parentOpt = projectionStore.getPerson(r.getParentId());
            Optional<ProjectionPerson> childOpt = projectionStore.getPerson(r.getChildId());
            if (parentOpt.isPresent() && parentOpt.get().isHidden()) {
                continue;
            }
            if (childOpt.isPresent() && childOpt.get().isHidden()) {
                continue;
            }
            
            String marriageId = r.getMarriageId() != null ? r.getMarriageId().toString() : null;
            dtos.add(new RelationshipDTO(
                    r.getId().toString(),
                    "PARENT_CHILD",
                    r.getSubtype(),
                    r.getParentId().toString(),
                    r.getChildId().toString(),
                    r.getRole(),
                    marriageId
            ));
        }
        return dtos;
    }
}
