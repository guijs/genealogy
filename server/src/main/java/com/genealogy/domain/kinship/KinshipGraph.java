package com.genealogy.domain.kinship;

import java.util.*;

public class KinshipGraph {
    private final List<Relation> relations = new ArrayList<>();
    private final Map<UUID, List<Relation>> childToParents = new HashMap<>();

    public void addRelation(Relation rel) {
        if (rel.getParentId().equals(rel.getChildId())) {
            throw new KinshipException(KinshipError.SELF_LOOP);
        }

        validateParentUniqueness(rel);

        if (wouldCreateCycle(rel)) {
            throw new KinshipException(KinshipError.CYCLE_DETECTED);
        }

        relations.add(rel);
        childToParents.computeIfAbsent(rel.getChildId(), k -> new ArrayList<>()).add(rel);
    }

    private void validateParentUniqueness(Relation newRel) {
        if (!newRel.getType().isParentRole()) {
            return;
        }

        List<Relation> existingParents = childToParents.getOrDefault(newRel.getChildId(), Collections.emptyList());
        for (Relation existing : existingParents) {
            if (existing.getType() == newRel.getType() && !existing.getParentId().equals(newRel.getParentId())) {
                switch (newRel.getType()) {
                    case BIOLOGICAL_FATHER:
                        throw new KinshipException(KinshipError.DUAL_BIOLOGICAL_FATHER);
                    case BIOLOGICAL_MOTHER:
                        throw new KinshipException(KinshipError.DUAL_BIOLOGICAL_MOTHER);
                    case ADOPTIVE_FATHER:
                        throw new KinshipException(KinshipError.DUAL_ADOPTIVE_FATHER);
                    case ADOPTIVE_MOTHER:
                        throw new KinshipException(KinshipError.DUAL_ADOPTIVE_MOTHER);
                }
            }
        }
    }

    private boolean wouldCreateCycle(Relation newRel) {
        Set<UUID> visited = new HashSet<>();
        return isAncestor(newRel.getParentId(), newRel.getChildId(), visited);
    }

    private boolean isAncestor(UUID current, UUID target, Set<UUID> visited) {
        if (current.equals(target)) {
            return true;
        }
        if (visited.contains(current)) {
            return false;
        }
        visited.add(current);

        List<Relation> parents = childToParents.getOrDefault(current, Collections.emptyList());
        for (Relation rel : parents) {
            if (isAncestor(rel.getParentId(), target, visited)) {
                return true;
            }
        }
        return false;
    }

    public List<Relation> getParents(UUID personId) {
        return new ArrayList<>(childToParents.getOrDefault(personId, Collections.emptyList()));
    }

    public List<Relation> getRelations() {
        return new ArrayList<>(relations);
    }
}
