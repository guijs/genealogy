package com.genealogy.domain.kinship;

public enum RelationType {
    BIOLOGICAL_FATHER("biological_father"),
    BIOLOGICAL_MOTHER("biological_mother"),
    ADOPTIVE_FATHER("adoptive_father"),
    ADOPTIVE_MOTHER("adoptive_mother");

    private final String value;

    RelationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isParentRole() {
        return true;
    }

    public boolean isBiologicalParent() {
        return this == BIOLOGICAL_FATHER || this == BIOLOGICAL_MOTHER;
    }

    public static RelationType fromString(String value) {
        for (RelationType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
