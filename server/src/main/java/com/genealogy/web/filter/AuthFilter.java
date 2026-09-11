package com.genealogy.web.filter;

/**
 * Authentication constants.
 * 
 * @deprecated X-User-Id header authentication is deprecated. Use Bearer JWT tokens instead.
 *             See Phase4-A implementation for JWT auth endpoints: POST /api/v1/auth/register and /api/v1/auth/login.
 */
public final class AuthFilter {

    /**
     * @deprecated Use Bearer JWT token in Authorization header instead.
     */
    @Deprecated
    public static final String USER_ID_HEADER = "X-User-Id";

    /**
     * Request attribute key for the authenticated user's UUID.
     * This attribute is set by JwtAuthenticationFilter after validating the JWT token.
     */
    public static final String USER_ID_ATTRIBUTE = "userId";

    private AuthFilter() {
    }
}
