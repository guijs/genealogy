package com.genealogy.domain.kinship;

public class KinshipException extends RuntimeException {
    private final KinshipError error;

    public KinshipException(KinshipError error) {
        super(error.getMessage());
        this.error = error;
    }

    public KinshipError getKinshipError() {
        return error;
    }
}
