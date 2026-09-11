package com.genealogy.domain.person;

import java.util.UUID;

public class Person {
    private final UUID id;
    private final UUID familyId;
    private final String firstName;
    private final String lastName;

    public Person(UUID id, UUID familyId, String firstName, String lastName) {
        this.id = id;
        this.familyId = familyId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
