package com.genealogy.web;

import com.genealogy.domain.family.Membership;
import com.genealogy.domain.story.Story;
import com.genealogy.service.PersonRefUpdateAction;
import com.genealogy.service.StoryService;
import com.genealogy.web.dto.CreateStoryRequest;
import com.genealogy.web.dto.DeleteStoryRequest;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.PersonRefRequest;
import com.genealogy.web.dto.StoriesListResponse;
import com.genealogy.web.dto.StoryResponse;
import com.genealogy.web.dto.UpdateStoryRequest;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/families/{familyId}/stories")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @GetMapping
    public ResponseEntity<?> listStories(HttpServletRequest request,
                                         @RequestParam(required = false) String personId) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID personIdFilter = null;
        if (personId != null && !personId.isBlank()) {
            try {
                personIdFilter = UUID.fromString(personId);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new ErrorResponse("invalid personId"));
            }
        }

        List<Story> stories = storyService.listStories(familyId, personIdFilter, membership.getRole());
        List<StoryResponse> responses = stories.stream()
                .map(StoryResponse::fromStory)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new StoriesListResponse(responses));
    }

    @GetMapping("/{storyId}")
    public ResponseEntity<?> getStory(HttpServletRequest request,
                                      @PathVariable String storyId) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID storyUUID;
        try {
            storyUUID = UUID.fromString(storyId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Optional<Story> storyOpt = storyService.getStory(familyId, storyUUID, membership.getRole());
        if (storyOpt.isEmpty()) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        return ResponseEntity.ok(StoryResponse.fromStory(storyOpt.get()));
    }

    @PostMapping
    public ResponseEntity<?> createStory(HttpServletRequest request,
                                         @RequestBody CreateStoryRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        LocalDate narrativeTime = null;
        if (body.getNarrativeTime() != null && !body.getNarrativeTime().isBlank()) {
            try {
                narrativeTime = LocalDate.parse(body.getNarrativeTime());
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(new ErrorResponse("invalid narrative_time format, expected YYYY-MM-DD"));
            }
        }

        List<UUID> personIds = parsePersonIds(body.getPersonIds());
        if (personIds == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("invalid person_ids format"));
        }

        List<StoryService.PersonRefInput> personRefs;
        try {
            personRefs = parsePersonRefs(body.getPersonRefs());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }

        try {
            Story story = storyService.createStory(
                    familyId,
                    membership.getUserId(),
                    body.getTitle(),
                    body.getBody(),
                    narrativeTime,
                    personIds,
                    personRefs,
                    membership.getRole()
            );
            return ResponseEntity.status(201).body(StoryResponse.fromStory(story));
        } catch (StoryService.WriteAccessDeniedException e) {
            return ResponseEntity.status(403).body(new ErrorResponse(e.getMessage()));
        } catch (StoryService.InvalidBodyException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (StoryService.InvalidPersonException e) {
            return ResponseEntity.status(409).body(new ErrorResponse(e.getMessage()));
        } catch (StoryService.InvalidPersonRefException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/{storyId}")
    public ResponseEntity<?> updateStory(HttpServletRequest request,
                                         @PathVariable String storyId,
                                         @RequestBody UpdateStoryRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID storyUUID;
        try {
            storyUUID = UUID.fromString(storyId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        if (body.getVersion() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("version is required for updates"));
        }

        LocalDate narrativeTime = null;
        if (body.getNarrativeTime() != null && !body.getNarrativeTime().isBlank()) {
            try {
                narrativeTime = LocalDate.parse(body.getNarrativeTime());
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(new ErrorResponse("invalid narrative_time format, expected YYYY-MM-DD"));
            }
        }

        List<UUID> personIds = parsePersonIds(body.getPersonIds());
        if (personIds == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("invalid person_ids format"));
        }

        PersonRefUpdateAction personRefAction;
        try {
            personRefAction = parsePersonRefUpdateAction(body.getPersonRefs());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }

        try {
            Story story = storyService.updateStory(
                    familyId,
                    storyUUID,
                    membership.getUserId(),
                    membership.getRole(),
                    body.getTitle(),
                    body.getBody(),
                    narrativeTime,
                    personIds,
                    personRefAction,
                    body.getVersion()
            );
            return ResponseEntity.ok(StoryResponse.fromStory(story));
        } catch (StoryService.WriteAccessDeniedException e) {
            return ResponseEntity.status(403).body(new ErrorResponse(e.getMessage()));
        } catch (StoryService.StoryNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (StoryService.VersionConflictException e) {
            return ResponseEntity.status(409).body(StoryResponse.fromStory(e.getCurrentStory()));
        } catch (StoryService.InvalidBodyException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (StoryService.InvalidPersonException e) {
            return ResponseEntity.status(409).body(new ErrorResponse(e.getMessage()));
        } catch (StoryService.InvalidPersonRefException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/{storyId}")
    public ResponseEntity<?> deleteStory(HttpServletRequest request,
                                         @PathVariable String storyId,
                                         @RequestBody(required = false) DeleteStoryRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID storyUUID;
        try {
            storyUUID = UUID.fromString(storyId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        if (body == null || body.getVersion() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("version is required for delete"));
        }

        try {
            storyService.deleteStory(familyId, storyUUID, membership.getRole(), body.getVersion());
            return ResponseEntity.noContent().build();
        } catch (StoryService.WriteAccessDeniedException e) {
            return ResponseEntity.status(403).body(new ErrorResponse(e.getMessage()));
        } catch (StoryService.StoryNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (StoryService.VersionConflictException e) {
            return ResponseEntity.status(409).body(StoryResponse.fromStory(e.getCurrentStory()));
        }
    }

    private List<UUID> parsePersonIds(List<String> personIdStrings) {
        if (personIdStrings == null) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (String idStr : personIdStrings) {
            try {
                result.add(UUID.fromString(idStr));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return result;
    }

    private List<StoryService.PersonRefInput> parsePersonRefs(List<PersonRefRequest> personRefRequests) {
        if (personRefRequests == null || personRefRequests.isEmpty()) {
            return null;
        }

        return personRefRequests.stream()
                .map(r -> {
                    UUID personId = null;
                    if (r.getPersonId() != null && !r.getPersonId().isBlank()) {
                        try {
                            personId = UUID.fromString(r.getPersonId());
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("invalid person_id format");
                        }
                    }
                    return new StoryService.PersonRefInput(personId, r.getDisplayNameSnapshot());
                })
                .collect(Collectors.toList());
    }

    private PersonRefUpdateAction parsePersonRefUpdateAction(List<PersonRefRequest> personRefRequests) {
        if (personRefRequests == null) {
            return new PersonRefUpdateAction.Omit();
        }

        if (personRefRequests.isEmpty()) {
            return new PersonRefUpdateAction.ClearAll();
        }

        List<PersonRefUpdateAction.Replace.PersonRefInput> personRefs = personRefRequests.stream()
                .map(r -> {
                    UUID personId = null;
                    if (r.getPersonId() != null && !r.getPersonId().isBlank()) {
                        try {
                            personId = UUID.fromString(r.getPersonId());
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("invalid person_id format");
                        }
                    }
                    return new PersonRefUpdateAction.Replace.PersonRefInput(personId, r.getDisplayNameSnapshot());
                })
                .collect(Collectors.toList());

        return new PersonRefUpdateAction.Replace(personRefs);
    }
}
