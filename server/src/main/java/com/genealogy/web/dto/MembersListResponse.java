package com.genealogy.web.dto;

import java.util.List;

public class MembersListResponse {
    private final List<MemberResponse> members;

    public MembersListResponse(List<MemberResponse> members) {
        this.members = members;
    }

    public List<MemberResponse> getMembers() {
        return members;
    }
}
