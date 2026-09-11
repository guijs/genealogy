package com.genealogy.web.dto;

public class UpdateMemberRoleRequest {
    private String role;

    public UpdateMemberRoleRequest() {
    }

    public UpdateMemberRoleRequest(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
