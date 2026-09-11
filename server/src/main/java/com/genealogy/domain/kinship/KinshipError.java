package com.genealogy.domain.kinship;

public enum KinshipError {
    SELF_LOOP("person cannot be their own parent"),
    CYCLE_DETECTED("relationship would create a cycle"),
    DUAL_BIOLOGICAL_FATHER("kinship: person already has a biological father"),
    DUAL_BIOLOGICAL_MOTHER("kinship: person already has a biological mother"),
    DUAL_ADOPTIVE_FATHER("kinship: person already has an adoptive father"),
    DUAL_ADOPTIVE_MOTHER("kinship: person already has an adoptive mother");

    private final String message;

    KinshipError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
