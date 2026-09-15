package com.genealogy.web;

import com.genealogy.domain.kinship.KinshipError;
import com.genealogy.domain.kinship.KinshipException;
import com.genealogy.domain.kinship.RelationType;
import com.genealogy.service.RelationshipService;
import com.genealogy.web.dto.AddRelationshipRequest;
import com.genealogy.web.dto.AddRelationshipResponse;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.RelationshipDissolveResponse;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/families/{familyId}/relationships")
public class RelationshipController {

    private final RelationshipService relationshipService;

    public RelationshipController(RelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @PostMapping
    public ResponseEntity<?> addRelationship(HttpServletRequest request,
                                              @RequestBody AddRelationshipRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        if (body.getParentId() == null || body.getParentId().isEmpty()) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid parent_id"));
        }
        if (body.getChildId() == null || body.getChildId().isEmpty()) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid child_id"));
        }
        if (body.getRelationshipType() == null || body.getRelationshipType().isEmpty()) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid relationship_type"));
        }

        UUID parentId;
        try {
            parentId = UUID.fromString(body.getParentId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid parent_id"));
        }

        UUID childId;
        try {
            childId = UUID.fromString(body.getChildId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid child_id"));
        }

        RelationType relationType = RelationType.fromString(body.getRelationshipType());
        if (relationType == null) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid relationship_type"));
        }

        try {
            relationshipService.addParentChild(familyId, parentId, childId, relationType);
        } catch (RelationshipService.PersonNotInFamilyException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (KinshipException e) {
            KinshipError error = e.getKinshipError();
            if (error == KinshipError.SELF_LOOP) {
                return ResponseEntity.status(422).body(new ErrorResponse("person cannot be their own parent"));
            }
            if (error == KinshipError.CYCLE_DETECTED) {
                return ResponseEntity.status(422).body(new ErrorResponse("relationship would create a cycle"));
            }
            return ResponseEntity.status(422).body(new ErrorResponse(e.getMessage()));
        }

        return ResponseEntity.status(201).body(new AddRelationshipResponse(true));
    }

    @PostMapping("/{id}/dissolve")
    public ResponseEntity<?> dissolveRelationship(HttpServletRequest request,
                                                   @PathVariable("id") String idParam) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID relationshipId;
        try {
            relationshipId = UUID.fromString(idParam);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        try {
            relationshipService.dissolve(familyId, relationshipId);
        } catch (RelationshipService.RelationshipNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (RelationshipService.AlreadyDissolvedException e) {
            return ResponseEntity.status(409).body(new ErrorResponse("relationship is already dissolved"));
        }

        return ResponseEntity.ok(new RelationshipDissolveResponse(relationshipId.toString(), true));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restoreRelationship(HttpServletRequest request,
                                                  @PathVariable("id") String idParam) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID relationshipId;
        try {
            relationshipId = UUID.fromString(idParam);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        try {
            relationshipService.restore(familyId, relationshipId);
        } catch (RelationshipService.RelationshipNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (RelationshipService.NotDissolvedException e) {
            return ResponseEntity.status(409).body(new ErrorResponse("relationship is not dissolved"));
        } catch (RelationshipService.RestoreBlockedException e) {
            return ResponseEntity.status(422).body(new ErrorResponse(e.getMessage()));
        }

        return ResponseEntity.ok(new RelationshipDissolveResponse(relationshipId.toString(), false));
    }
}
