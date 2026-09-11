package com.genealogy.domain.projection;

import java.util.UUID;

public class ProjectionPerson {
    private final UUID id;
    private final UUID familyId;
    private final String displayName;
    private final Gender gender;
    private final Integer birthYear;
    private final Integer deathYear;
    private final boolean hidden;

    public ProjectionPerson(UUID id, UUID familyId, String displayName, Gender gender,
                            Integer birthYear, Integer deathYear, Boolean hidden) {
        this.id = id;
        this.familyId = familyId;
        this.displayName = displayName;
        this.gender = gender;
        this.birthYear = birthYear;
        this.deathYear = deathYear;
        this.hidden = hidden != null && hidden;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Gender getGender() {
        return gender;
    }

    public Integer getBirthYear() {
        return birthYear;
    }

    public Integer getDeathYear() {
        return deathYear;
    }

    public boolean isHidden() {
        return hidden;
    }

    public ProjectionPerson copy() {
        return new ProjectionPerson(id, familyId, displayName, gender, birthYear, deathYear, hidden);
    }
}
