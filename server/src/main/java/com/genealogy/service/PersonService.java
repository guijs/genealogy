package com.genealogy.service;

import com.genealogy.domain.person.Person;
import com.genealogy.domain.projection.ProjectionPerson;
import com.genealogy.store.PersonStore;
import com.genealogy.store.ProjectionStore;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class PersonService {
    public static final int MAX_PERSONS_PER_FAMILY = 10000;

    private final PersonStore personStore;
    private final ProjectionStore projectionStore;

    public PersonService(PersonStore personStore, ProjectionStore projectionStore) {
        this.personStore = personStore;
        this.projectionStore = projectionStore;
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
                    false);
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
}
