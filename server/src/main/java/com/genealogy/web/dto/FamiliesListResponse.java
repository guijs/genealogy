package com.genealogy.web.dto;

import java.util.List;

public class FamiliesListResponse {
    private final List<FamilyResponse> families;

    public FamiliesListResponse(List<FamilyResponse> families) {
        this.families = families;
    }

    public List<FamilyResponse> getFamilies() {
        return families;
    }
}
