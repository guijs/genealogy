package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.store.FamilyStore;
import com.genealogy.web.dto.CreateFamilyRequest;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.FamilyResponse;
import com.genealogy.web.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/families")
public class FamiliesController {

    private final FamilyStore familyStore;

    public FamiliesController(FamilyStore familyStore) {
        this.familyStore = familyStore;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createFamily(HttpServletRequest request, @RequestBody CreateFamilyRequest body) {
        UUID userId = (UUID) request.getAttribute(AuthFilter.USER_ID_ATTRIBUTE);
        if (userId == null) {
            return ResponseEntity.status(401).body(new ErrorResponse("authentication required"));
        }

        String name = body.getName();
        if (name == null || name.isBlank()) {
            return ResponseEntity.status(400).body(new ErrorResponse("name is required"));
        }

        UUID familyId = UUID.randomUUID();
        familyStore.createFamily(familyId, name.trim());
        familyStore.addMemberWithRole(familyId, userId, Role.ADMIN);

        return ResponseEntity.status(201).body(new FamilyResponse(familyId.toString(), name.trim()));
    }
}
