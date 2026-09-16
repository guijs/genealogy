package com.genealogy.web;

import com.genealogy.domain.family.Family;
import com.genealogy.domain.family.Membership;
import com.genealogy.domain.family.Role;
import com.genealogy.store.FamilyStore;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.GenerationNamesRequest;
import com.genealogy.web.dto.GenerationNamesResponse;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/families/{familyId}")
public class GenerationNamesController {

    private static final int MAX_GENERATION_NAMES = 200;
    private static final int MAX_NAME_LENGTH = 16;

    private final FamilyStore familyStore;

    public GenerationNamesController(FamilyStore familyStore) {
        this.familyStore = familyStore;
    }

    @GetMapping("/generation-names")
    public ResponseEntity<?> getGenerationNames(HttpServletRequest request) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Optional<Family> familyOpt = familyStore.getFamily(familyId);
        if (familyOpt.isEmpty()) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Family family = familyOpt.get();
        return ResponseEntity.ok(new GenerationNamesResponse(
                family.getGenerationNames(),
                family.getGenerationNameAlign()));
    }

    @PutMapping("/generation-names")
    @Transactional
    public ResponseEntity<?> setGenerationNames(HttpServletRequest request,
                                                 @RequestBody GenerationNamesRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null || membership.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(new ErrorResponse("admin access required"));
        }

        Optional<Family> familyOpt = familyStore.getFamily(familyId);
        if (familyOpt.isEmpty()) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        List<String> names = body.getGenerationNames();
        String align = body.getGenerationNameAlign();

        if (names != null && names.size() > MAX_GENERATION_NAMES) {
            return ResponseEntity.status(400).body(new ErrorResponse(
                    "generation_names exceeds maximum of " + MAX_GENERATION_NAMES + " entries"));
        }

        if (names != null) {
            for (String name : names) {
                if (name != null && name.length() > MAX_NAME_LENGTH) {
                    return ResponseEntity.status(400).body(new ErrorResponse(
                            "each generation name must be at most " + MAX_NAME_LENGTH + " characters"));
                }
            }
        }

        if (align == null) {
            align = familyOpt.get().getGenerationNameAlign();
        } else if (!align.equals("A") && !align.equals("B")) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid generation_name_align, must be 'A' or 'B'"));
        }

        familyStore.updateGenerationNames(familyId, names, align);

        return ResponseEntity.ok(new GenerationNamesResponse(names, align));
    }
}
