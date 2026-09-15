package com.genealogy.service;

import com.genealogy.domain.family.Family;
import com.genealogy.domain.projection.ParentChildSubtype;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.projection.ProjectionRelationship;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.web.dto.LineagePersonDTO;
import com.genealogy.web.dto.LineageResponse;
import com.genealogy.web.dto.LineageGenerationDTO;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LineageService {

    private final FamilyStore familyStore;
    private final ProjectionStore projectionStore;

    public LineageService(FamilyStore familyStore, ProjectionStore projectionStore) {
        this.familyStore = familyStore;
        this.projectionStore = projectionStore;
    }

    public static class PersonNotFoundException extends RuntimeException {
        public PersonNotFoundException(String message) {
            super(message);
        }
    }

    public static class PersonNotInFamilyException extends RuntimeException {
        public PersonNotInFamilyException(String message) {
            super(message);
        }
    }

    public static class HiddenPersonException extends RuntimeException {
        public HiddenPersonException(String message) {
            super(message);
        }
    }

    public LineageResponse getLineage(UUID familyId) {
        Optional<Family> familyOpt = familyStore.getFamily(familyId);
        if (familyOpt.isEmpty()) {
            return null;
        }

        Family family = familyOpt.get();
        UUID progenitorId = family.getProgenitorPersonId();

        if (progenitorId == null) {
            return new LineageResponse(
                    familyId.toString(),
                    null,
                    Collections.emptyList()
            );
        }

        return buildLineage(familyId, progenitorId);
    }

    public void setProgenitor(UUID familyId, UUID personId) {
        if (personId != null) {
            Optional<ProjectionPerson> personOpt = projectionStore.getPerson(personId);
            if (personOpt.isEmpty()) {
                throw new PersonNotFoundException("person not found");
            }
            ProjectionPerson person = personOpt.get();
            if (!person.getFamilyId().equals(familyId)) {
                throw new PersonNotInFamilyException("person not in family");
            }
            if (person.isHidden()) {
                throw new HiddenPersonException("cannot set hidden person as progenitor");
            }
        }
        familyStore.updateProgenitor(familyId, personId);
    }

    private LineageResponse buildLineage(UUID familyId, UUID progenitorId) {
        Optional<ProjectionPerson> progenitorOpt = projectionStore.getPerson(progenitorId);
        if (progenitorOpt.isEmpty() || !progenitorOpt.get().getFamilyId().equals(familyId)) {
            return new LineageResponse(familyId.toString(), null, Collections.emptyList());
        }

        ProjectionPerson progenitor = progenitorOpt.get();
        if (progenitor.isHidden()) {
            return new LineageResponse(familyId.toString(), null, Collections.emptyList());
        }

        Map<UUID, Integer> personIndex = new HashMap<>();
        Map<UUID, Boolean> personConflict = new HashMap<>();
        Map<Integer, List<UUID>> generationPersons = new TreeMap<>();

        Queue<UUID> queue = new LinkedList<>();
        queue.add(progenitorId);
        personIndex.put(progenitorId, 1);
        personConflict.put(progenitorId, false);

        while (!queue.isEmpty()) {
            UUID currentId = queue.poll();
            int currentIndex = personIndex.get(currentId);

            List<ProjectionRelationship> childRels = projectionStore.getChildrenOf(currentId);
            for (ProjectionRelationship rel : childRels) {
                if (rel.isDissolved()) {
                    continue;
                }
                if (rel.getSubtype() != ParentChildSubtype.BIOLOGICAL) {
                    continue;
                }
                if (!rel.getFamilyId().equals(familyId)) {
                    continue;
                }

                UUID childId = rel.getChildId();
                Optional<ProjectionPerson> childOpt = projectionStore.getPerson(childId);
                if (childOpt.isEmpty()) {
                    continue;
                }
                ProjectionPerson child = childOpt.get();
                if (child.isHidden()) {
                    continue;
                }

                int childIndex = currentIndex + 1;

                if (personIndex.containsKey(childId)) {
                    int existingIndex = personIndex.get(childId);
                    if (existingIndex != childIndex) {
                        personConflict.put(childId, true);
                    }
                } else {
                    personIndex.put(childId, childIndex);
                    personConflict.put(childId, false);
                    queue.add(childId);
                }
            }
        }

        for (Map.Entry<UUID, Integer> entry : personIndex.entrySet()) {
            UUID personId = entry.getKey();
            int index = entry.getValue();
            generationPersons.computeIfAbsent(index, k -> new ArrayList<>()).add(personId);
        }

        List<LineageGenerationDTO> generations = new ArrayList<>();
        for (Map.Entry<Integer, List<UUID>> entry : generationPersons.entrySet()) {
            int index = entry.getKey();
            List<UUID> personIds = entry.getValue();

            List<LineagePersonDTO> persons = new ArrayList<>();
            for (UUID personId : personIds) {
                Optional<ProjectionPerson> personOpt = projectionStore.getPerson(personId);
                if (personOpt.isEmpty()) {
                    continue;
                }
                ProjectionPerson person = personOpt.get();
                boolean conflict = personConflict.getOrDefault(personId, false);
                persons.add(new LineagePersonDTO(
                        personId.toString(),
                        person.getDisplayName(),
                        conflict
                ));
            }

            persons.sort(Comparator.comparing(LineagePersonDTO::getId));

            generations.add(new LineageGenerationDTO(index, persons));
        }

        return new LineageResponse(
                familyId.toString(),
                progenitorId.toString(),
                generations
        );
    }
}
