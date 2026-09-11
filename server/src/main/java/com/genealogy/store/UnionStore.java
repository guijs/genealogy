package com.genealogy.store;

import com.genealogy.domain.union.Union;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class UnionStore {
    private final Map<UUID, Union> unions = new ConcurrentHashMap<>();

    public void addUnion(Union union) {
        unions.put(union.getId(), union);
    }

    public Optional<Union> getUnion(UUID id) {
        Union u = unions.get(id);
        return u != null ? Optional.of(u.copy()) : Optional.empty();
    }

    public Optional<Union> getUnionInFamily(UUID id, UUID familyId) {
        Union u = unions.get(id);
        if (u != null && u.getFamilyId().equals(familyId)) {
            return Optional.of(u.copy());
        }
        return Optional.empty();
    }

    public List<Union> listByFamily(UUID familyId) {
        return unions.values().stream()
                .filter(u -> u.getFamilyId().equals(familyId))
                .map(Union::copy)
                .collect(Collectors.toList());
    }

    public List<Union> getActiveUnionsForPerson(UUID personId) {
        return unions.values().stream()
                .filter(u -> u.involvesPartner(personId) && u.isActive())
                .map(Union::copy)
                .collect(Collectors.toList());
    }

    public boolean hasActiveUnion(UUID personId) {
        return unions.values().stream()
                .anyMatch(u -> u.involvesPartner(personId) && u.isActive());
    }

    public void updateUnion(Union union) {
        unions.put(union.getId(), union);
    }

    public void clear() {
        unions.clear();
    }
}
