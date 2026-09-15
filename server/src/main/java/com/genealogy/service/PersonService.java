package com.genealogy.service;

import com.genealogy.domain.family.Family;
import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import com.genealogy.store.UnionStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PersonService {
    public static final int MAX_PERSONS_PER_FAMILY = 10000;

    private final PersonStore personStore;
    private final ProjectionStore projectionStore;
    private final UnionStore unionStore;
    private final FamilyStore familyStore;

    public PersonService(PersonStore personStore, ProjectionStore projectionStore, UnionStore unionStore, FamilyStore familyStore) {
        this.personStore = personStore;
        this.projectionStore = projectionStore;
        this.unionStore = unionStore;
        this.familyStore = familyStore;
    }

    public static class PersonCapExceededException extends RuntimeException {
        public PersonCapExceededException() {
            super("family has reached the maximum number of persons");
        }
    }

    public static class PersonNotFoundException extends RuntimeException {
        public PersonNotFoundException() {
            super("person not found");
        }
    }

    public static class InvalidNameException extends RuntimeException {
        public InvalidNameException(String message) {
            super(message);
        }
    }

    public static class ActiveUnionRequiresConfirmException extends RuntimeException {
        public ActiveUnionRequiresConfirmException() {
            super("person has an active union; set confirm_hide_with_active_union to true or end the union first");
        }
    }

    public static class PersonAlreadyHiddenException extends RuntimeException {
        public PersonAlreadyHiddenException() {
            super("person is already hidden");
        }
    }

    public static class PersonNotHiddenException extends RuntimeException {
        public PersonNotHiddenException() {
            super("person is not hidden");
        }
    }

    @Transactional
    public Person createPerson(UUID familyId, String firstName, String lastName) {
        if (isBlank(firstName) && isBlank(lastName)) {
            throw new InvalidNameException("at least one name field is required");
        }
        if (firstName != null && firstName.isBlank()) {
            throw new InvalidNameException("first_name cannot be blank");
        }
        if (lastName != null && lastName.isBlank()) {
            throw new InvalidNameException("last_name cannot be blank");
        }

        long count = personStore.countByFamily(familyId);
        if (count >= MAX_PERSONS_PER_FAMILY) {
            throw new PersonCapExceededException();
        }

        UUID personId = UUID.randomUUID();
        String normalizedFirstName = firstName != null ? firstName.trim() : null;
        String normalizedLastName = lastName != null ? lastName.trim() : null;

        Person person = new Person(personId, familyId, normalizedFirstName, normalizedLastName);
        personStore.addPerson(person);

        String displayName = buildDisplayName(normalizedFirstName, normalizedLastName);
        ProjectionPerson projectionPerson = new ProjectionPerson(
                personId, familyId, displayName, null, null, null, false);
        projectionStore.upsertPerson(projectionPerson);

        return person;
    }

    @Transactional
    public Person updatePerson(UUID familyId, UUID personId, String firstName, String lastName) {
        Optional<Person> existingOpt = personStore.getPerson(personId);
        if (existingOpt.isEmpty() || !existingOpt.get().getFamilyId().equals(familyId)) {
            throw new PersonNotFoundException();
        }

        Person existing = existingOpt.get();

        String newFirstName = firstName != null ? firstName.trim() : existing.getFirstName();
        String newLastName = lastName != null ? lastName.trim() : existing.getLastName();

        if (firstName != null && firstName.isBlank()) {
            throw new InvalidNameException("first_name cannot be blank");
        }
        if (lastName != null && lastName.isBlank()) {
            throw new InvalidNameException("last_name cannot be blank");
        }

        Person updated = new Person(personId, familyId, newFirstName, newLastName);
        personStore.replace(updated);

        String displayName = buildDisplayName(newFirstName, newLastName);
        Optional<ProjectionPerson> existingProjectionOpt = projectionStore.getPerson(personId);
        ProjectionPerson projectionPerson;
        if (existingProjectionOpt.isPresent()) {
            ProjectionPerson existingProjection = existingProjectionOpt.get();
            projectionPerson = new ProjectionPerson(
                    personId, familyId, displayName, 
                    existingProjection.getGender(),
                    existingProjection.getBirthYear(),
                    existingProjection.getDeathYear(),
                    existingProjection.isHidden());
        } else {
            projectionPerson = new ProjectionPerson(personId, familyId, displayName, null, null, null, false);
        }
        projectionStore.upsertPerson(projectionPerson);

        return updated;
    }

    private String buildDisplayName(String firstName, String lastName) {
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isEmpty()) {
            sb.append(firstName);
        }
        if (lastName != null && !lastName.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(lastName);
        }
        return sb.toString().trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Transactional
    public void hidePerson(UUID familyId, UUID personId, boolean confirmActiveUnion) {
        Optional<Person> existingOpt = personStore.getPerson(personId);
        if (existingOpt.isEmpty() || !existingOpt.get().getFamilyId().equals(familyId)) {
            throw new PersonNotFoundException();
        }

        Optional<ProjectionPerson> projectionOpt = projectionStore.getPerson(personId);
        if (projectionOpt.isEmpty()) {
            throw new PersonNotFoundException();
        }

        ProjectionPerson existing = projectionOpt.get();
        if (existing.isHidden()) {
            return;
        }

        boolean hasActiveUnion = unionStore.hasActiveUnion(personId);
        if (hasActiveUnion && !confirmActiveUnion) {
            throw new ActiveUnionRequiresConfirmException();
        }

        ProjectionPerson updated = new ProjectionPerson(
                existing.getId(),
                existing.getFamilyId(),
                existing.getDisplayName(),
                existing.getGender(),
                existing.getBirthYear(),
                existing.getDeathYear(),
                true
        );
        projectionStore.upsertPerson(updated);

        clearProgenitorIfMatches(familyId, personId);
    }

    private void clearProgenitorIfMatches(UUID familyId, UUID personId) {
        Optional<Family> familyOpt = familyStore.getFamily(familyId);
        if (familyOpt.isPresent()) {
            Family family = familyOpt.get();
            if (personId.equals(family.getProgenitorPersonId())) {
                familyStore.updateProgenitor(familyId, null);
            }
        }
    }

    @Transactional
    public void restorePerson(UUID familyId, UUID personId) {
        Optional<Person> existingOpt = personStore.getPerson(personId);
        if (existingOpt.isEmpty() || !existingOpt.get().getFamilyId().equals(familyId)) {
            throw new PersonNotFoundException();
        }

        Optional<ProjectionPerson> projectionOpt = projectionStore.getPerson(personId);
        if (projectionOpt.isEmpty()) {
            throw new PersonNotFoundException();
        }

        ProjectionPerson existing = projectionOpt.get();
        if (!existing.isHidden()) {
            return;
        }

        ProjectionPerson updated = new ProjectionPerson(
                existing.getId(),
                existing.getFamilyId(),
                existing.getDisplayName(),
                existing.getGender(),
                existing.getBirthYear(),
                existing.getDeathYear(),
                false
        );
        projectionStore.upsertPerson(updated);
    }
}
