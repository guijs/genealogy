package com.genealogy.web;

import com.genealogy.domain.union.Union;
import com.genealogy.service.UnionService;
import com.genealogy.web.dto.CreateUnionRequest;
import com.genealogy.web.dto.EndUnionRequest;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.UnionResponse;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/families/{familyId}/unions")
public class UnionController {

    private final UnionService unionService;

    public UnionController(UnionService unionService) {
        this.unionService = unionService;
    }

    @PostMapping
    public ResponseEntity<?> createUnion(HttpServletRequest request,
                                         @RequestBody CreateUnionRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        if (body.getPartnerAId() == null || body.getPartnerAId().isEmpty()) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid partner_a_id"));
        }
        if (body.getPartnerBId() == null || body.getPartnerBId().isEmpty()) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid partner_b_id"));
        }

        UUID partnerAId;
        try {
            partnerAId = UUID.fromString(body.getPartnerAId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid partner_a_id"));
        }

        UUID partnerBId;
        try {
            partnerBId = UUID.fromString(body.getPartnerBId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid partner_b_id"));
        }

        try {
            Union union = unionService.createUnion(familyId, partnerAId, partnerBId, body.getStartedAt());
            return ResponseEntity.status(201).body(UnionResponse.fromUnion(union));
        } catch (UnionService.SelfUnionException e) {
            return ResponseEntity.status(422).body(new ErrorResponse("cannot create union with oneself"));
        } catch (UnionService.PartnerNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (UnionService.ActiveUnionExistsException e) {
            return ResponseEntity.status(422).body(new ErrorResponse("partner already has an active union; end existing union first"));
        }
    }

    @PostMapping("/{unionId}/end")
    public ResponseEntity<?> endUnion(HttpServletRequest request,
                                      @PathVariable String unionId,
                                      @RequestBody(required = false) EndUnionRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID unionUUID;
        try {
            unionUUID = UUID.fromString(unionId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        String endedReason = null;
        String endedAt = null;
        if (body != null) {
            endedReason = body.getEndedReason();
            endedAt = body.getEndedAt();
        }

        try {
            Union union = unionService.endUnion(familyId, unionUUID, endedReason, endedAt);
            return ResponseEntity.status(200).body(UnionResponse.fromUnion(union));
        } catch (UnionService.UnionNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (UnionService.UnionAlreadyEndedException e) {
            return ResponseEntity.status(422).body(new ErrorResponse("union has already ended"));
        }
    }
}
