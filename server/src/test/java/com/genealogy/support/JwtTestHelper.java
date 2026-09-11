package com.genealogy.support;

import com.genealogy.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Test helper for generating JWT tokens in tests.
 */
@Component
public class JwtTestHelper {

    @Autowired
    private JwtService jwtService;

    /**
     * Generate a valid JWT token for the given user ID.
     */
    public String generateToken(UUID userId) {
        return jwtService.generateToken(userId);
    }

    /**
     * Generate a Bearer authorization header value.
     */
    public String bearerToken(UUID userId) {
        return "Bearer " + generateToken(userId);
    }
}
