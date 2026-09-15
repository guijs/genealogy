package com.genealogy.store;

/**
 * Exception thrown when a storage key attempts to escape the configured storage root
 * via path traversal (e.g., "../" sequences) or absolute paths.
 */
public class PathTraversalException extends RuntimeException {
    public PathTraversalException(String message) {
        super(message);
    }
}
