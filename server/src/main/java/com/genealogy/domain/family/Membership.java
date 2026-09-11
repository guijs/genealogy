package com.genealogy.domain.family;

import java.util.UUID;

public class Membership {
    private final UUID userId;
    private final Role role;

    public Membership(UUID userId, Role role) {
        this.userId = userId;
        this.role = role;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }
}
