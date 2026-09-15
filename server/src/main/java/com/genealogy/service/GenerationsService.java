package com.genealogy.service;

import com.genealogy.domain.projection.ParentChildSubtype;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.projection.ProjectionRelationship;
import com.genealogy.store.ProjectionStore;
import com.genealogy.web.dto.GenerationDTO;
import com.genealogy.web.dto.GenerationPersonDTO;
import com.genealogy.web.dto.GenerationsProjectionResponse;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for computing Ego-relative generation layers.
 * 
 * BFS traversal from Ego (focusPersonId):
 * - Ego is at generation index 0
 * - Parents of Ego → -1, grandparents → -2, etc.
 * - Children of Ego → +1, grandchildren → +2, etc.
 * - Only biological parent/child edges are traversed (adoptive edges ignored for climb)
 * - Dissolved edges are excluded
 * - Hidden persons are excluded
 * - Conflict detection: if a person is reachable via paths with different indices,
 *   they are marked with conflict=true but keep first BFS discovery index
 */
@Service
public class GenerationsService {

    private final ProjectionStore projectionStore;

    public GenerationsService(ProjectionStore projectionStore) {
        this.projectionStore = projectionStore;
    }

    public static class PersonNotInFamilyException extends RuntimeException {
        public PersonNotInFamilyException() {
            super("person not in family");
        }
    }

    public GenerationsProjectionResponse getGenerations(UUID familyId, UUID focusPersonId) {
        Optional<ProjectionPerson> focusOpt = projectionStore.getPerson(focusPersonId);
        if (focusOpt.isEmpty() || !focusOpt.get().getFamilyId().equals(familyId)) {
            throw new PersonNotInFamilyException();
        }

        ProjectionPerson focusPerson = focusOpt.get();
        if (focusPerson.isHidden()) {
            throw new PersonNotInFamilyException();
        }

        Map<UUID, Integer> personToIndex = new HashMap<>();
        Set<UUID> conflictPersons = new HashSet<>();
        Map<UUID, ProjectionPerson> visitedPersons = new HashMap<>();

        Queue<BfsEntry> queue = new LinkedList<>();
        queue.add(new BfsEntry(focusPersonId, 0));
        personToIndex.put(focusPersonId, 0);
        visitedPersons.put(focusPersonId, focusPerson);

        while (!queue.isEmpty()) {
            BfsEntry entry = queue.poll();
            UUID currentId = entry.personId;
            int currentIndex = entry.generationIndex;

            List<ProjectionRelationship> parentRels = projectionStore.getParentsOf(currentId);
            for (ProjectionRelationship r : parentRels) {
                if (!r.getFamilyId().equals(familyId)) {
                    continue;
                }
                if (r.isDissolved()) {
                    continue;
                }
                if (r.getSubtype() != ParentChildSubtype.BIOLOGICAL) {
                    continue;
                }

                UUID parentId = r.getParentId();
                Optional<ProjectionPerson> parentOpt = projectionStore.getPerson(parentId);
                if (parentOpt.isEmpty() || !parentOpt.get().getFamilyId().equals(familyId)) {
                    continue;
                }
                ProjectionPerson parent = parentOpt.get();
                if (parent.isHidden()) {
                    continue;
                }

                int parentIndex = currentIndex - 1;
                if (personToIndex.containsKey(parentId)) {
                    if (personToIndex.get(parentId) != parentIndex) {
                        conflictPersons.add(parentId);
                    }
                } else {
                    personToIndex.put(parentId, parentIndex);
                    visitedPersons.put(parentId, parent);
                    queue.add(new BfsEntry(parentId, parentIndex));
                }
            }

            List<ProjectionRelationship> childRels = projectionStore.getChildrenOf(currentId);
            for (ProjectionRelationship r : childRels) {
                if (!r.getFamilyId().equals(familyId)) {
                    continue;
                }
                if (r.isDissolved()) {
                    continue;
                }
                if (r.getSubtype() != ParentChildSubtype.BIOLOGICAL) {
                    continue;
                }

                UUID childId = r.getChildId();
                Optional<ProjectionPerson> childOpt = projectionStore.getPerson(childId);
                if (childOpt.isEmpty() || !childOpt.get().getFamilyId().equals(familyId)) {
                    continue;
                }
                ProjectionPerson child = childOpt.get();
                if (child.isHidden()) {
                    continue;
                }

                int childIndex = currentIndex + 1;
                if (personToIndex.containsKey(childId)) {
                    if (personToIndex.get(childId) != childIndex) {
                        conflictPersons.add(childId);
                    }
                } else {
                    personToIndex.put(childId, childIndex);
                    visitedPersons.put(childId, child);
                    queue.add(new BfsEntry(childId, childIndex));
                }
            }
        }

        Map<Integer, List<GenerationPersonDTO>> generationMap = new TreeMap<>();
        for (Map.Entry<UUID, Integer> e : personToIndex.entrySet()) {
            UUID personId = e.getKey();
            int index = e.getValue();
            ProjectionPerson person = visitedPersons.get(personId);
            boolean isConflict = conflictPersons.contains(personId);

            generationMap.computeIfAbsent(index, k -> new ArrayList<>())
                    .add(new GenerationPersonDTO(personId.toString(), person.getDisplayName(), isConflict));
        }

        for (List<GenerationPersonDTO> persons : generationMap.values()) {
            persons.sort((a, b) -> {
                int cmp = a.getDisplayName().compareTo(b.getDisplayName());
                if (cmp != 0) return cmp;
                return a.getId().compareTo(b.getId());
            });
        }

        List<GenerationDTO> generations = new ArrayList<>();
        for (Map.Entry<Integer, List<GenerationPersonDTO>> e : generationMap.entrySet()) {
            generations.add(new GenerationDTO(e.getKey(), e.getValue()));
        }

        return new GenerationsProjectionResponse(
                familyId.toString(),
                focusPersonId.toString(),
                generations
        );
    }

    private static class BfsEntry {
        final UUID personId;
        final int generationIndex;

        BfsEntry(UUID personId, int generationIndex) {
            this.personId = personId;
            this.generationIndex = generationIndex;
        }
    }
}
