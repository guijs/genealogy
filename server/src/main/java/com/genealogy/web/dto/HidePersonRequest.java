package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class HidePersonRequest {
    @JsonProperty("confirm_hide_with_active_union")
    private Boolean confirmHideWithActiveUnion;

    public HidePersonRequest() {
    }

    public HidePersonRequest(Boolean confirmHideWithActiveUnion) {
        this.confirmHideWithActiveUnion = confirmHideWithActiveUnion;
    }

    public Boolean getConfirmHideWithActiveUnion() {
        return confirmHideWithActiveUnion;
    }

    public void setConfirmHideWithActiveUnion(Boolean confirmHideWithActiveUnion) {
        this.confirmHideWithActiveUnion = confirmHideWithActiveUnion;
    }

    public boolean isConfirmHideWithActiveUnion() {
        return confirmHideWithActiveUnion != null && confirmHideWithActiveUnion;
    }
}
