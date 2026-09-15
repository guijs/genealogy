package com.genealogy.service;

import com.genealogy.domain.projection.*;
import com.genealogy.store.ProjectionStore;
import com.genealogy.web.dto.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
        List<DerivedSiblingDTO> siblingDTOs = deriveSiblings(relationships, persons);

        String truncateReason = truncated.get() ? TRUNCATE_REASON : null;

        return new GraphProjectionResponse(
                familyId.toString(),
                rootPersonId.toString(),
                depth,
                truncated.get(),
                truncateReason,
                personDTOs,
                marriageDTOs,
                relationshipDTOs,
                siblingDTOs
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

    /**
     * Derive sibling relationships from biological parent edges.
     * P0 derivation rules:
     * - Only biological parent edges (not adoptive)
     * - Dissolved edges do not participate
     * - Hidden persons are excluded
     * - Share both biological parents → full sibling
     * - Share only biological father → paternal_half
     * - Share only biological mother → maternal_half
     * - Pairs are emitted once with canonicalized IDs (personId < siblingId)
     */
    private List<DerivedSiblingDTO> deriveSiblings(Map<UUID, ProjectionRelationship> relationships,
                                                    Map<UUID, ProjectionPerson> persons) {
        Map<UUID, Set<UUID>> bioFathers = new HashMap<>();
        Map<UUID, Set<UUID>> bioMothers = new HashMap<>();

        for (ProjectionRelationship r : relationships.values()) {
            if (r.isDissolved()) {
                continue;
            }
            if (r.getSubtype() != ParentChildSubtype.BIOLOGICAL) {
                continue;
            }

            UUID childId = r.getChildId();
            UUID parentId = r.getParentId();

            ProjectionPerson child = persons.get(childId);
            ProjectionPerson parent = persons.get(parentId);
            if (child == null || parent == null) {
                continue;
            }
            if (child.isHidden() || parent.isHidden()) {
                continue;
            }

            ParentRole role = r.getRole();
            if (role == ParentRole.FATHER) {
                bioFathers.computeIfAbsent(childId, k -> new HashSet<>()).add(parentId);
            } else if (role == ParentRole.MOTHER) {
                bioMothers.computeIfAbsent(childId, k -> new HashSet<>()).add(parentId);
            }
        }

        Map<UUID, Set<UUID>> childrenOfFather = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : bioFathers.entrySet()) {
            UUID childId = entry.getKey();
            for (UUID fatherId : entry.getValue()) {
                childrenOfFather.computeIfAbsent(fatherId, k -> new HashSet<>()).add(childId);
            }
        }

        Map<UUID, Set<UUID>> childrenOfMother = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : bioMothers.entrySet()) {
            UUID childId = entry.getKey();
            for (UUID motherId : entry.getValue()) {
                childrenOfMother.computeIfAbsent(motherId, k -> new HashSet<>()).add(childId);
            }
        }

        Set<String> processedPairs = new HashSet<>();
        List<DerivedSiblingDTO> siblings = new ArrayList<>();

        Set<UUID> visiblePersonIds = persons.values().stream()
                .filter(p -> !p.isHidden())
                .map(ProjectionPerson::getId)
                .collect(Collectors.toSet());

        for (UUID personId : visiblePersonIds) {
            Set<UUID> sharedFatherSiblings = new HashSet<>();
            Set<UUID> sharedMotherSiblings = new HashSet<>();
            Set<UUID> fatherIds = bioFathers.getOrDefault(personId, Collections.emptySet());
            Set<UUID> motherIds = bioMothers.getOrDefault(personId, Collections.emptySet());

            for (UUID fatherId : fatherIds) {
                Set<UUID> fatherChildren = childrenOfFather.getOrDefault(fatherId, Collections.emptySet());
                for (UUID siblingId : fatherChildren) {
                    if (!siblingId.equals(personId) && visiblePersonIds.contains(siblingId)) {
                        sharedFatherSiblings.add(siblingId);
                    }
                }
            }

            for (UUID motherId : motherIds) {
                Set<UUID> motherChildren = childrenOfMother.getOrDefault(motherId, Collections.emptySet());
                for (UUID siblingId : motherChildren) {
                    if (!siblingId.equals(personId) && visiblePersonIds.contains(siblingId)) {
                        sharedMotherSiblings.add(siblingId);
                    }
                }
            }

            Set<UUID> allSiblings = new HashSet<>();
            allSiblings.addAll(sharedFatherSiblings);
            allSiblings.addAll(sharedMotherSiblings);

            for (UUID siblingId : allSiblings) {
                String pairKey = canonicalizePair(personId, siblingId);
                if (processedPairs.contains(pairKey)) {
                    continue;
                }
                processedPairs.add(pairKey);

                boolean sharesFather = sharedFatherSiblings.contains(siblingId);
                boolean sharesMother = sharedMotherSiblings.contains(siblingId);

                DerivedSiblingDTO.SiblingKind kind;
                List<String> sharedParentIds = new ArrayList<>();

                if (sharesFather && sharesMother) {
                    kind = DerivedSiblingDTO.SiblingKind.full;
                    Set<UUID> personFatherSet = bioFathers.getOrDefault(personId, Collections.emptySet());
                    Set<UUID> siblingFatherSet = bioFathers.getOrDefault(siblingId, Collections.emptySet());
                    Set<UUID> commonFathers = new HashSet<>(personFatherSet);
                    commonFathers.retainAll(siblingFatherSet);
                    for (UUID fid : commonFathers) {
                        sharedParentIds.add(fid.toString());
                    }

                    Set<UUID> personMotherSet = bioMothers.getOrDefault(personId, Collections.emptySet());
                    Set<UUID> siblingMotherSet = bioMothers.getOrDefault(siblingId, Collections.emptySet());
                    Set<UUID> commonMothers = new HashSet<>(personMotherSet);
                    commonMothers.retainAll(siblingMotherSet);
                    for (UUID mid : commonMothers) {
                        sharedParentIds.add(mid.toString());
                    }
                } else if (sharesFather) {
                    kind = DerivedSiblingDTO.SiblingKind.paternal_half;
                    Set<UUID> personFatherSet = bioFathers.getOrDefault(personId, Collections.emptySet());
                    Set<UUID> siblingFatherSet = bioFathers.getOrDefault(siblingId, Collections.emptySet());
                    Set<UUID> commonFathers = new HashSet<>(personFatherSet);
                    commonFathers.retainAll(siblingFatherSet);
                    for (UUID fid : commonFathers) {
                        sharedParentIds.add(fid.toString());
                    }
                } else {
                    kind = DerivedSiblingDTO.SiblingKind.maternal_half;
                    Set<UUID> personMotherSet = bioMothers.getOrDefault(personId, Collections.emptySet());
                    Set<UUID> siblingMotherSet = bioMothers.getOrDefault(siblingId, Collections.emptySet());
                    Set<UUID> commonMothers = new HashSet<>(personMotherSet);
                    commonMothers.retainAll(siblingMotherSet);
                    for (UUID mid : commonMothers) {
                        sharedParentIds.add(mid.toString());
                    }
                }

                String[] canonicalIds = pairKey.split(":");
                siblings.add(new DerivedSiblingDTO(canonicalIds[0], canonicalIds[1], kind, sharedParentIds));
            }
        }

        return siblings;
    }

    private String canonicalizePair(UUID id1, UUID id2) {
        String s1 = id1.toString();
        String s2 = id2.toString();
        if (s1.compareTo(s2) < 0) {
            return s1 + ":" + s2;
        } else {
            return s2 + ":" + s1;
        }
    }
}
