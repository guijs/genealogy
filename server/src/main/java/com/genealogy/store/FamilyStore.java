package com.genealogy.store;

import com.genealogy.domain.family.Family;
import com.genealogy.domain.family.Membership;
import com.genealogy.domain.family.Role;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FamilyStore {
    private final Map<UUID, Family> families = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Membership>> memberships = new ConcurrentHashMap<>();

    public void createFamily(UUID familyId, String name) {
        families.put(familyId, new Family(familyId, name));
        memberships.put(familyId, new ConcurrentHashMap<>());
    }

    public boolean familyExists(UUID familyId) {
        return families.containsKey(familyId);
    }

    public Optional<Family> getFamily(UUID familyId) {
        return Optional.ofNullable(families.get(familyId));
    }

    public void addMember(UUID familyId, UUID userId) {
        addMemberWithRole(familyId, userId, Role.ADMIN);
    }

    public void addMemberWithRole(UUID familyId, UUID userId, Role role) {
        memberships.computeIfAbsent(familyId, k -> new ConcurrentHashMap<>())
                .put(userId, new Membership(userId, role));
    }

    public boolean isMember(UUID familyId, UUID userId) {
        Map<UUID, Membership> familyMembers = memberships.get(familyId);
        return familyMembers != null && familyMembers.containsKey(userId);
    }

    public Optional<Membership> getMembership(UUID familyId, UUID userId) {
        Map<UUID, Membership> familyMembers = memberships.get(familyId);
        if (familyMembers == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(familyMembers.get(userId));
    }

    public void clear() {
        families.clear();
        memberships.clear();
    }
}
