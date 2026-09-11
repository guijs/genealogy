package com.genealogy.web;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.person.Person;
import com.genealogy.store.FamilyStore;
import com.genealogy.store.PersonStore;
import com.genealogy.web.filter.AuthFilter;
import com.genealogy.web.filter.FamilyMembershipFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FamilyController.class)
@Import({FamilyStore.class, PersonStore.class, AuthFilter.class, FamilyMembershipFilter.class})
class FamilyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FamilyStore familyStore;

    @Autowired
    private PersonStore personStore;

    private static final UUID FAMILY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NON_MEMBER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PERSON1_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PERSON2_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String FAMILY_NAME = "Test Family";

    @BeforeEach
    void setUp() {
        familyStore.clear();
        personStore.clear();
        
        familyStore.createFamily(FAMILY_ID, FAMILY_NAME);
        familyStore.addMemberWithRole(FAMILY_ID, MEMBER_USER_ID, Role.ADMIN);
        
        personStore.addPerson(new Person(PERSON1_ID, FAMILY_ID, "John", "Doe"));
        personStore.addPerson(new Person(PERSON2_ID, FAMILY_ID, "Jane", "Doe"));
    }

    // Test 1: No X-User-Id → 401 on GetFamily
    @Test
    void getFamily_withoutUserIdHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication required"));
    }

    // Test 2: Invalid X-User-Id → 401
    @Test
    void getFamily_withInvalidUserId_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID)
                        .header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid user id"));
    }

    // Test 3: Valid user, non-member → 404 on GetFamily
    @Test
    void getFamily_withNonMemberUser_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID)
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Test 3b: Valid user, non-member → 404 on ListPersons
    @Test
    void listPersons_withNonMemberUser_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", NON_MEMBER_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Test 4: Member → 200 GetFamily with id+name
    @Test
    void getFamily_withMemberUser_returns200WithFamilyData() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID)
                        .header("X-User-Id", MEMBER_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(FAMILY_ID.toString()))
                .andExpect(jsonPath("$.name").value(FAMILY_NAME));
    }

    // Test 5: Member → 200 ListPersons with seeded persons
    @Test
    void listPersons_withMemberUser_returns200WithPersons() throws Exception {
        mockMvc.perform(get("/api/v1/families/" + FAMILY_ID + "/persons")
                        .header("X-User-Id", MEMBER_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons").isArray())
                .andExpect(jsonPath("$.persons.length()").value(2))
                .andExpect(jsonPath("$.persons[0].id").exists())
                .andExpect(jsonPath("$.persons[0].first_name").exists())
                .andExpect(jsonPath("$.persons[0].last_name").exists());
    }

    // Test 6: Unknown familyId → 404
    @Test
    void getFamily_withUnknownFamilyId_returns404() throws Exception {
        UUID unknownFamilyId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        mockMvc.perform(get("/api/v1/families/" + unknownFamilyId)
                        .header("X-User-Id", MEMBER_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    // Additional test: Invalid familyId format → 404
    @Test
    void getFamily_withInvalidFamilyIdFormat_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/families/not-a-uuid")
                        .header("X-User-Id", MEMBER_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }
}
