package com.genealogy.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.domain.family.Membership;
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
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Order(2)
public class WriteAccessFilter extends OncePerRequestFilter {

    private static final Pattern RELATIONSHIPS_PATH_PATTERN = 
            Pattern.compile("^/api/v1/families/[^/]+/relationships$");

    private static final Pattern MEDIA_UPLOAD_URL_PATH_PATTERN = 
            Pattern.compile("^/api/v1/families/[^/]+/media/upload-url$");

    private static final Pattern PERSONS_POST_PATH_PATTERN = 
            Pattern.compile("^/api/v1/families/[^/]+/persons$");

    private static final Pattern PERSONS_PATCH_PATH_PATTERN = 
            Pattern.compile("^/api/v1/families/[^/]+/persons/[^/]+$");

    private static final Pattern UNIONS_POST_PATH_PATTERN = 
            Pattern.compile("^/api/v1/families/[^/]+/unions$");

    private static final Pattern UNIONS_END_PATH_PATTERN = 
            Pattern.compile("^/api/v1/families/[^/]+/unions/[^/]+/end$");

    private static final Pattern PERSONS_HIDE_PATH_PATTERN = 
            Pattern.compile("^/api/v1/families/[^/]+/persons/[^/]+/hide$");

    private static final Pattern PERSONS_RESTORE_PATH_PATTERN = 
            Pattern.compile("^/api/v1/families/[^/]+/persons/[^/]+/restore$");

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!isWriteEndpoint(path, method)) {
            filterChain.doFilter(request, response);
            return;
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!membership.getRole().canWrite()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "write access required");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWriteEndpoint(String path, String method) {
        if (!WRITE_METHODS.contains(method)) {
            return false;
        }
        if (RELATIONSHIPS_PATH_PATTERN.matcher(path).matches()) {
            return true;
        }
        if ("POST".equals(method) && PERSONS_POST_PATH_PATTERN.matcher(path).matches()) {
            return true;
        }
        if ("PATCH".equals(method) && PERSONS_PATCH_PATH_PATTERN.matcher(path).matches()) {
            return true;
        }
        if ("POST".equals(method) && UNIONS_POST_PATH_PATTERN.matcher(path).matches()) {
            return true;
        }
        if ("POST".equals(method) && UNIONS_END_PATH_PATTERN.matcher(path).matches()) {
            return true;
        }
        if ("POST".equals(method) && PERSONS_HIDE_PATH_PATTERN.matcher(path).matches()) {
            return true;
        }
        if ("POST".equals(method) && PERSONS_RESTORE_PATH_PATTERN.matcher(path).matches()) {
            return true;
        }
        if ("POST".equals(method) && MEDIA_UPLOAD_URL_PATH_PATTERN.matcher(path).matches()) {
            return true;
        }
        return false;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", message));
    }
}
