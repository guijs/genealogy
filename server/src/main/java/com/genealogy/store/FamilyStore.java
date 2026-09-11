package com.genealogy.store;

import com.genealogy.domain.family.Family;
import com.genealogy.domain.family.Membership;
import com.genealogy.domain.family.Role;
import com.genealogy.mapper.FamilyMapper;
import com.genealogy.mapper.FamilyMemberMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class FamilyStore {
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;

    public FamilyStore(FamilyMapper familyMapper, FamilyMemberMapper familyMemberMapper) {
        this.familyMapper = familyMapper;
        this.familyMemberMapper = familyMemberMapper;
    }

    public void createFamily(UUID familyId, String name) {
        familyMapper.insert(familyId, name);
    }

    public boolean familyExists(UUID familyId) {
        return familyMapper.existsById(familyId);
    }

    public Optional<Family> getFamily(UUID familyId) {
        return Optional.ofNullable(familyMapper.findById(familyId));
    }

    public void addMember(UUID familyId, UUID userId) {
        addMemberWithRole(familyId, userId, Role.ADMIN);
    }

    public void addMemberWithRole(UUID familyId, UUID userId, Role role) {
        familyMemberMapper.insert(familyId, userId, role.getValue());
    }

    public boolean isMember(UUID familyId, UUID userId) {
        return familyMemberMapper.existsByFamilyAndUser(familyId, userId);
    }

    public Optional<Membership> getMembership(UUID familyId, UUID userId) {
        return Optional.ofNullable(familyMemberMapper.findByFamilyAndUser(familyId, userId));
    }

    public void clear() {
        familyMemberMapper.deleteAll();
        familyMapper.deleteAll();
    }
}
