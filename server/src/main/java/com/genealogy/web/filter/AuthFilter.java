package com.genealogy.web.filter;

/**
 * Authentication constants for JWT-based authentication.
 */
public final class AuthFilter {

    /**
     * Request attribute key for the authenticated user's UUID.
     * This attribute is set by JwtAuthenticationFilter after validating the JWT token.
     */
    public static final String USER_ID_ATTRIBUTE = "userId";

    private AuthFilter() {
    }
}
