package com.genealogy.web;

import com.genealogy.service.GenerationsService;
import com.genealogy.service.GraphService;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.GenerationsProjectionResponse;
import com.genealogy.web.dto.GraphProjectionResponse;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/families/{familyId}")
public class GraphController {

    private final GraphService graphService;
    private final GenerationsService generationsService;

    public GraphController(GraphService graphService, GenerationsService generationsService) {
        this.graphService = graphService;
        this.generationsService = generationsService;
    }

    @GetMapping("/graph")
    public ResponseEntity<?> getGraph(HttpServletRequest request,
                                       @RequestParam(required = false) String rootPersonId,
                                       @RequestParam(required = false) String depth) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        if (rootPersonId == null || rootPersonId.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("rootPersonId query parameter required"));
        }

        UUID rootPersonUUID;
        try {
            rootPersonUUID = UUID.fromString(rootPersonId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("invalid rootPersonId"));
        }

        int depthValue = GraphService.DEFAULT_DEPTH;
        if (depth != null && !depth.isEmpty()) {
            try {
                int parsedDepth = Integer.parseInt(depth);
                if (parsedDepth > 0) {
                    depthValue = parsedDepth;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (depthValue > GraphService.MAX_DEPTH) {
            return ResponseEntity.badRequest().body(new ErrorResponse("depth exceeds maximum allowed value of 8"));
        }

        try {
            GraphProjectionResponse graph = graphService.getGraph(familyId, rootPersonUUID, depthValue);
            return ResponseEntity.ok(graph);
        } catch (GraphService.PersonNotInFamilyException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }
    }

    /**
     * GET /generations - Ego-relative generation layers.
     * 
     * focusPersonId is optional:
     * - If provided and valid in family: use as Ego
     * - If omitted/empty: fallback to earliest created non-hidden person in family
     * - If family has zero non-hidden persons: 404 with "no persons in family"
     * 
     * Client selection strategy (documented, not enforced by API):
     * - "本人节点" (self node) if user has one
     * - "会话上次焦点" (session's last focus) if available
     * - Otherwise omit and let backend resolve to earliest person
     */
    @GetMapping("/generations")
    public ResponseEntity<?> getGenerations(HttpServletRequest request,
                                            @RequestParam(required = false) String focusPersonId) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID focusPersonUUID = null;
        if (focusPersonId != null && !focusPersonId.isEmpty()) {
            try {
                focusPersonUUID = UUID.fromString(focusPersonId);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new ErrorResponse("invalid focusPersonId"));
            }
        }

        try {
            GenerationsProjectionResponse generations = generationsService.getGenerations(familyId, focusPersonUUID);
            return ResponseEntity.ok(generations);
        } catch (GenerationsService.PersonNotInFamilyException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (GenerationsService.NoPersonsInFamilyException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("no persons in family"));
        }
    }
}
