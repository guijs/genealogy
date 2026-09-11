package com.genealogy.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.domain.family.Membership;
import com.genealogy.store.FamilyStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class FamilyMembershipFilter extends OncePerRequestFilter {

    public static final String FAMILY_ID_ATTRIBUTE = "familyId";
    public static final String MEMBERSHIP_ATTRIBUTE = "membership";

    private static final Pattern FAMILY_PATH_PATTERN = 
            Pattern.compile("^/api/v1/families/([^/]+)(?:/.*)?$");

    private final FamilyStore familyStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FamilyMembershipFilter(FamilyStore familyStore) {
        this.familyStore = familyStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        
        Matcher matcher = FAMILY_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            filterChain.doFilter(request, response);
            return;
        }

        String familyIdStr = matcher.group(1);
        
        UUID familyId;
        try {
            familyId = UUID.fromString(familyIdStr);
        } catch (IllegalArgumentException e) {
            writeNotFound(response);
            return;
        }

        if (!familyStore.familyExists(familyId)) {
            writeNotFound(response);
            return;
        }

        UUID userId = (UUID) request.getAttribute(AuthFilter.USER_ID_ATTRIBUTE);
        if (userId == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
            return;
        }

        Optional<Membership> membership = familyStore.getMembership(familyId, userId);
        if (membership.isEmpty()) {
            writeNotFound(response);
            return;
        }

        request.setAttribute(FAMILY_ID_ATTRIBUTE, familyId);
        request.setAttribute(MEMBERSHIP_ATTRIBUTE, membership.get());
        filterChain.doFilter(request, response);
    }

    private void writeNotFound(HttpServletResponse response) throws IOException {
        writeError(response, HttpServletResponse.SC_NOT_FOUND, "not found");
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", message));
    }
}
