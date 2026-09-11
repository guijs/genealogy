package com.genealogy.web;

import com.genealogy.service.GraphService;
import com.genealogy.web.dto.ErrorResponse;
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

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
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
}
