package com.genealogy.web.dto;

import java.util.List;

public class PersonRefCandidatesResponse {
    private final List<PersonRefCandidateResponse> candidates;

    public PersonRefCandidatesResponse(List<PersonRefCandidateResponse> candidates) {
        this.candidates = candidates;
    }

    public List<PersonRefCandidateResponse> getCandidates() {
        return candidates;
    }
}
