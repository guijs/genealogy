package com.genealogy.store;

import com.genealogy.domain.union.Union;
import com.genealogy.mapper.UnionMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UnionStore {
    private final UnionMapper unionMapper;

    public UnionStore(UnionMapper unionMapper) {
        this.unionMapper = unionMapper;
    }

    public void addUnion(Union union) {
        unionMapper.insert(
                union.getId(),
                union.getFamilyId(),
                union.getPartnerAId(),
                union.getPartnerBId(),
                union.getStatus() != null ? union.getStatus().getValue() : "active",
                union.getStartedAt(),
                union.getEndedAt(),
                union.getEndedReason()
        );
    }

    public Optional<Union> getUnion(UUID id) {
        Union u = unionMapper.findById(id);
        return u != null ? Optional.of(u.copy()) : Optional.empty();
    }

    public Optional<Union> getUnionInFamily(UUID id, UUID familyId) {
        Union u = unionMapper.findByIdAndFamilyId(id, familyId);
        return u != null ? Optional.of(u.copy()) : Optional.empty();
    }

    public List<Union> listByFamily(UUID familyId) {
        return unionMapper.findByFamilyId(familyId);
    }

    public List<Union> getActiveUnionsForPerson(UUID personId) {
        return unionMapper.findActiveByPartnerId(personId);
    }

    public boolean hasActiveUnion(UUID personId) {
        return unionMapper.hasActiveUnion(personId);
    }

    public void updateUnion(Union union) {
        unionMapper.update(
                union.getId(),
                union.getStatus() != null ? union.getStatus().getValue() : "active",
                union.getEndedAt(),
                union.getEndedReason()
        );
    }

    public void clear() {
        unionMapper.deleteAll();
    }
}
