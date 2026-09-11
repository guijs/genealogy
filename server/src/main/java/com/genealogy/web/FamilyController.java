package com.genealogy.web;

import com.genealogy.domain.family.Family;
import com.genealogy.domain.family.Membership;
import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.service.PersonService;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.web.dto.AddMemberRequest;
import com.genealogy.web.dto.AddMemberResponse;
import com.genealogy.web.dto.CreatePersonRequest;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.FamilyResponse;
import com.genealogy.web.dto.HidePersonRequest;
import com.genealogy.web.dto.MemberResponse;
import com.genealogy.web.dto.MembersListResponse;
import com.genealogy.web.dto.PersonResponse;
import com.genealogy.web.dto.PersonsListResponse;
import com.genealogy.web.dto.UpdatePersonRequest;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/families/{familyId}")
public class FamilyController {

    private final FamilyStore familyStore;
    private final PersonStore personStore;
    private final PersonService personService;

    public FamilyController(FamilyStore familyStore, PersonStore personStore, PersonService personService) {
        this.familyStore = familyStore;
        this.personStore = personStore;
        this.personService = personService;
    }

    @GetMapping
    public ResponseEntity<?> getFamily(HttpServletRequest request) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Optional<Family> family = familyStore.getFamily(familyId);
        if (family.isEmpty()) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Family fam = family.get();
        return ResponseEntity.ok(new FamilyResponse(fam.getId().toString(), fam.getName()));
    }

    @GetMapping("/persons")
    public ResponseEntity<?> listPersons(HttpServletRequest request) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        List<Person> persons = personStore.listByFamily(familyId);
        List<PersonResponse> personResponses = persons.stream()
                .map(p -> new PersonResponse(
                        p.getId().toString(),
                        p.getFirstName(),
                        p.getLastName()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PersonsListResponse(personResponses));
    }

    @PostMapping("/persons")
    public ResponseEntity<?> createPerson(HttpServletRequest request,
                                          @RequestBody CreatePersonRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        try {
            Person person = personService.createPerson(familyId, body.getFirstName(), body.getLastName());
            return ResponseEntity.status(201).body(new PersonResponse(
                    person.getId().toString(),
                    person.getFirstName(),
                    person.getLastName()));
        } catch (PersonService.InvalidNameException e) {
            return ResponseEntity.status(400).body(new ErrorResponse(e.getMessage()));
        } catch (PersonService.PersonCapExceededException e) {
            return ResponseEntity.status(409).body(new ErrorResponse(e.getMessage()));
        }
    }

    @PatchMapping("/persons/{personId}")
    public ResponseEntity<?> updatePerson(HttpServletRequest request,
                                          @PathVariable String personId,
                                          @RequestBody UpdatePersonRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID personUUID;
        try {
            personUUID = UUID.fromString(personId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        try {
            Person person = personService.updatePerson(familyId, personUUID, body.getFirstName(), body.getLastName());
            return ResponseEntity.ok(new PersonResponse(
                    person.getId().toString(),
                    person.getFirstName(),
                    person.getLastName()));
        } catch (PersonService.PersonNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (PersonService.InvalidNameException e) {
            return ResponseEntity.status(400).body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/persons/{personId}/hide")
    public ResponseEntity<?> hidePerson(HttpServletRequest request,
                                        @PathVariable String personId,
                                        @RequestBody(required = false) HidePersonRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID personUUID;
        try {
            personUUID = UUID.fromString(personId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        boolean confirmActiveUnion = body != null && body.isConfirmHideWithActiveUnion();

        try {
            personService.hidePerson(familyId, personUUID, confirmActiveUnion);
            return ResponseEntity.ok().build();
        } catch (PersonService.PersonNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        } catch (PersonService.ActiveUnionRequiresConfirmException e) {
            return ResponseEntity.status(409).body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/persons/{personId}/restore")
    public ResponseEntity<?> restorePerson(HttpServletRequest request,
                                           @PathVariable String personId) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        UUID personUUID;
        try {
            personUUID = UUID.fromString(personId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        try {
            personService.restorePerson(familyId, personUUID);
            return ResponseEntity.ok().build();
        } catch (PersonService.PersonNotFoundException e) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }
    }

    @PostMapping("/members")
    @Transactional
    public ResponseEntity<?> addMember(HttpServletRequest request,
                                       @RequestBody AddMemberRequest body) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        Membership membership = (Membership) request.getAttribute(FamilyMembershipFilter.MEMBERSHIP_ATTRIBUTE);
        if (membership == null || membership.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(new ErrorResponse("admin access required"));
        }

        if (body.getUserId() == null || body.getUserId().isBlank()) {
            return ResponseEntity.status(400).body(new ErrorResponse("user_id is required"));
        }

        UUID newUserId;
        try {
            newUserId = UUID.fromString(body.getUserId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid user_id"));
        }

        if (body.getRole() == null || body.getRole().isBlank()) {
            return ResponseEntity.status(400).body(new ErrorResponse("role is required"));
        }

        Role role;
        try {
            role = Role.valueOf(body.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(new ErrorResponse("invalid role"));
        }

        if (familyStore.isMember(familyId, newUserId)) {
            return ResponseEntity.status(409).body(new ErrorResponse("user is already a member"));
        }

        familyStore.addMemberWithRole(familyId, newUserId, role);

        return ResponseEntity.status(201).body(new AddMemberResponse(newUserId.toString(), role.getValue()));
    }

    @GetMapping("/members")
    public ResponseEntity<?> listMembers(HttpServletRequest request) {
        UUID familyId = (UUID) request.getAttribute(FamilyMembershipFilter.FAMILY_ID_ATTRIBUTE);
        if (familyId == null) {
            return ResponseEntity.status(404).body(new ErrorResponse("not found"));
        }

        List<Membership> memberships = familyStore.listMembers(familyId);
        List<MemberResponse> memberResponses = memberships.stream()
                .map(m -> new MemberResponse(
                        m.getUserId().toString(),
                        m.getRole().getValue()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new MembersListResponse(memberResponses));
    }
}
