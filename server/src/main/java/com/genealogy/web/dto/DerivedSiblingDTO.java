package com.genealogy.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Derived sibling relationship DTO for graph projection response.
 * Siblings are derived-only (never a writable fact) based on shared biological parents.
 *
 * <p>P0 derivation rules (product-relationship-rules v0.2 / PRD-P0-06 / AP-R9):
 * <ul>
 *   <li>Share ≥1 biological father OR mother → siblings</li>
 *   <li>Share both biological parents → full sibling</li>
 *   <li>Share only biological father → paternal_half sibling</li>
 *   <li>Share only biological mother → maternal_half sibling</li>
 *   <li>Adoptive-shared parents do NOT count as siblings in P0</li>
 *   <li>Dissolved bio parent edges do not participate in derivation</li>
 * </ul>
 *
 * <p>Pairs are emitted once with canonicalized IDs (personId < siblingId lexicographically).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DerivedSiblingDTO {

    public enum SiblingKind {
        full,
        paternal_half,
        maternal_half
    }

    private final String personId;
    private final String siblingId;
    private final SiblingKind kind;
    private final List<String> sharedParentIds;

    public DerivedSiblingDTO(String personId, String siblingId, SiblingKind kind, List<String> sharedParentIds) {
        this.personId = personId;
        this.siblingId = siblingId;
        this.kind = kind;
        this.sharedParentIds = sharedParentIds;
    }

    public String getPersonId() {
        return personId;
    }

    public String getSiblingId() {
        return siblingId;
    }

    public SiblingKind getKind() {
        return kind;
    }

    public List<String> getSharedParentIds() {
        return sharedParentIds;
    }
}
