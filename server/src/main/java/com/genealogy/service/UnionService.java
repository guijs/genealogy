package com.genealogy.service;

import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.MarriageStatus;
import com.genealogy.domain.projection.ProjectionMarriage;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.domain.union.Union;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.store.UnionStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UnionService {
    private final UnionStore unionStore;
    private final PersonStore personStore;
    private final ProjectionStore projectionStore;

    public UnionService(UnionStore unionStore, PersonStore personStore, ProjectionStore projectionStore) {
        this.unionStore = unionStore;
        this.personStore = personStore;
        this.projectionStore = projectionStore;
    }

    public Union createUnion(UUID familyId, UUID partnerAId, UUID partnerBId, String startedAt) {
        if (partnerAId.equals(partnerBId)) {
            throw new SelfUnionException();
        }

        if (!personStore.existsInFamily(partnerAId, familyId)) {
            throw new PartnerNotFoundException("partner_a_id");
        }
        if (!personStore.existsInFamily(partnerBId, familyId)) {
            throw new PartnerNotFoundException("partner_b_id");
        }

        if (unionStore.hasActiveUnion(partnerAId)) {
            throw new ActiveUnionExistsException(partnerAId);
        }
        if (unionStore.hasActiveUnion(partnerBId)) {
            throw new ActiveUnionExistsException(partnerBId);
        }

        UUID unionId = UUID.randomUUID();
        Union union = new Union(
                unionId,
                familyId,
                partnerAId,
                partnerBId,
                MarriageStatus.ACTIVE,
                startedAt,
                null,
                null
        );

        unionStore.addUnion(union);
        syncUnionToProjection(union);

        return union;
    }

    public Union endUnion(UUID familyId, UUID unionId, String endedReason, String endedAt) {
        Union union = unionStore.getUnionInFamily(unionId, familyId)
                .orElseThrow(UnionNotFoundException::new);

        if (!union.isActive()) {
            throw new UnionAlreadyEndedException();
        }

        union.end(endedReason, endedAt);
        unionStore.updateUnion(union);
        updateProjectionMarriage(union);

        return union;
    }

    private void syncUnionToProjection(Union union) {
        Person partnerA = personStore.getPerson(union.getPartnerAId()).orElse(null);
        Person partnerB = personStore.getPerson(union.getPartnerBId()).orElse(null);

        if (partnerA != null) {
            upsertProjectionPerson(partnerA);
        }
        if (partnerB != null) {
            upsertProjectionPerson(partnerB);
        }

        ProjectionMarriage projMarriage = new ProjectionMarriage(
                union.getId(),
                union.getFamilyId(),
                union.getPartnerAId(),
                union.getPartnerBId(),
                union.getStatus(),
                union.getStartedAt(),
                union.getEndedAt(),
                union.getEndedReason()
        );
        projectionStore.createMarriage(projMarriage);
    }

    private void updateProjectionMarriage(Union union) {
        ProjectionMarriage projMarriage = new ProjectionMarriage(
                union.getId(),
                union.getFamilyId(),
                union.getPartnerAId(),
                union.getPartnerBId(),
                union.getStatus(),
                union.getStartedAt(),
                union.getEndedAt(),
                union.getEndedReason()
        );
        projectionStore.updateMarriage(projMarriage);
    }

    private void upsertProjectionPerson(Person person) {
        if (projectionStore.getPerson(person.getId()).isPresent()) {
            return;
        }

        String displayName = buildDisplayName(person.getFirstName(), person.getLastName());
        ProjectionPerson projPerson = new ProjectionPerson(
                person.getId(),
                person.getFamilyId(),
                displayName,
                Gender.UNKNOWN,
                null,
                null,
                false
        );
        projectionStore.createPerson(projPerson);
    }

    private String buildDisplayName(String firstName, String lastName) {
        String first = firstName != null ? firstName : "";
        String last = lastName != null ? lastName : "";
        return (first + " " + last).trim();
    }

    public static class PartnerNotFoundException extends RuntimeException {
        private final String field;

        public PartnerNotFoundException(String field) {
            super("partner not found in family");
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    public static class SelfUnionException extends RuntimeException {
        public SelfUnionException() {
            super("cannot create union with oneself");
        }
    }

    public static class ActiveUnionExistsException extends RuntimeException {
        private final UUID personId;

        public ActiveUnionExistsException(UUID personId) {
            super("person already has an active union");
            this.personId = personId;
        }

        public UUID getPersonId() {
            return personId;
        }
    }

    public static class UnionNotFoundException extends RuntimeException {
        public UnionNotFoundException() {
            super("union not found");
        }
    }

    public static class UnionAlreadyEndedException extends RuntimeException {
        public UnionAlreadyEndedException() {
            super("union has already ended");
        }
    }
}
