package com.genealogy.service;

import java.util.List;
import java.util.UUID;

/**
 * Represents the three possible actions for mention updates during comment editing.
 *
 * <p>On PUT update comment:</p>
 * <ul>
 *   <li>{@link Omit} - mentions field was omitted (Jackson → null), do not touch existing mentions</li>
 *   <li>{@link ClearAll} - explicit empty array [], clear all mentions including historical left/removed</li>
 *   <li>{@link Replace} - explicit non-empty array, replace only active/current-member writable set,
 *       preserving mentions for users who are no longer current members</li>
 * </ul>
 */
public sealed interface MentionUpdateAction {

    /**
     * Mentions field was omitted from the request body.
     * Action: Do not touch existing mention rows (only update body/version).
     */
    record Omit() implements MentionUpdateAction {}

    /**
     * Explicit empty array [] was provided.
     * Action: Clear all mentions for this comment, including historical left/removed.
     */
    record ClearAll() implements MentionUpdateAction {}

    /**
     * Explicit non-empty list was provided.
     * Action: Replace the active/current-member writable set only.
     * Preserve existing mention rows whose user is no longer a current member (left/removed historical snapshots).
     */
    record Replace(List<MentionInput> mentions) implements MentionUpdateAction {
        public record MentionInput(UUID userId, String displayNameSnapshot) {}
    }
}
