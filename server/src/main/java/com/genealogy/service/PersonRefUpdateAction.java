package com.genealogy.service;

import java.util.List;
import java.util.UUID;

/**
 * Represents the three possible actions for person_ref updates during story/comment editing.
 *
 * <p>On PUT update:</p>
 * <ul>
 *   <li>{@link Omit} - person_refs field was omitted (Jackson → null), do not touch existing refs</li>
 *   <li>{@link ClearAll} - explicit empty array [], clear all person_refs</li>
 *   <li>{@link Replace} - explicit non-empty array, replace all person_refs</li>
 * </ul>
 */
public sealed interface PersonRefUpdateAction {

    /**
     * person_refs field was omitted from the request body.
     * Action: Do not touch existing person_ref rows (only update body/version).
     */
    record Omit() implements PersonRefUpdateAction {}

    /**
     * Explicit empty array [] was provided.
     * Action: Clear all person_refs for this story/comment.
     */
    record ClearAll() implements PersonRefUpdateAction {}

    /**
     * Explicit non-empty list was provided.
     * Action: Replace all person_refs.
     */
    record Replace(List<PersonRefInput> personRefs) implements PersonRefUpdateAction {
        public record PersonRefInput(UUID personId, String displayNameSnapshot) {}
    }
}
