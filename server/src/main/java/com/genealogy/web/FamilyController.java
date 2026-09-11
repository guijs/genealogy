package com.genealogy.web;

import com.genealogy.domain.family.Family;
import com.genealogy.domain.person.Person;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.web.dto.ErrorResponse;
import com.genealogy.web.dto.FamilyResponse;
import com.genealogy.web.dto.PersonResponse;
import com.genealogy.web.dto.PersonsListResponse;
import com.genealogy.web.filter.FamilyMembershipFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/families/{familyId}")
public class FamilyController {

    private final FamilyStore familyStore;
    private final PersonStore personStore;

    public FamilyController(FamilyStore familyStore, PersonStore personStore) {
        this.familyStore = familyStore;
        this.personStore = personStore;
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
}
