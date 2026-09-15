package com.genealogy.web;

import com.genealogy.domain.family.Membership;
import com.genealogy.domain.family.Role;
import com.genealogy.service.LineageService;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.LineageResponse;
import com.genealogy.web.dto.SetProgenitorRequest;
import com.genealogy.web.dto.SetProgenitorResponse;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/families/{familyId}")
public class LineageController {

    private final LineageService lineageService;

    public LineageController(LineageService lineageService) {
        this.lineageService = lineageService;
    }

    @GetMapping("/lineage")
    public ResponseEntity<?> getLineage(HttpServletRequest request) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        LineageResponse response = lineageService.getLineage(familyId);
        if (response == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        return ResponseEntity.ok(response);
    }

    @PutMapping("/progenitor")
    @Transactional
    public ResponseEntity<?> setProgenitor(HttpServletRequest request,
                                           @RequestBody SetProgenitorRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null || membership.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(new ErrorResponse("admin access required"));
        }

        UUID personId = null;
        if (body.getPersonId() != null && !body.getPersonId().isBlank()) {
            try {
                personId = UUID.fromString(body.getPersonId());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(400).body(new ErrorResponse("invalid person_id"));
            }
        }

        try {
            lineageService.setProgenitor(familyId, personId);
            String responsePersonId = personId != null ? personId.toString() : null;
            return ResponseEntity.ok(new SetProgenitorResponse(responsePersonId));
        } catch (LineageService.PersonNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("person not found"));
        } catch (LineageService.PersonNotInFamilyException e) {
            return ResponseEntity.status(400).body(new ErrorResponse("person not in family"));
        } catch (LineageService.HiddenPersonException e) {
            return ResponseEntity.status(400).body(new ErrorResponse("cannot set hidden person as progenitor"));
        }
    }
}
