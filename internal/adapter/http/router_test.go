package httpapi_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"
	httpapi "github.com/guijs/genealogy/internal/adapter/http"
	"github.com/guijs/genealogy/internal/app"
	"github.com/guijs/genealogy/internal/domain/family"
	"github.com/guijs/genealogy/internal/domain/kinship"
	"github.com/guijs/genealogy/internal/domain/person"
)

func setupTestRouter() (*httpapi.RouterDeps, *http.Handler) {
	membershipStore := family.NewInMemoryMembershipStore()
	personStore := person.NewInMemoryStore()
	kinshipStore := kinship.NewInMemoryStore()
	relationshipService := app.NewRelationshipService(personStore, kinshipStore)

	deps := &httpapi.RouterDeps{
		MembershipStore:     membershipStore,
		RelationshipService: relationshipService,
	}

	router := httpapi.NewRouter(*deps)
	var handler http.Handler = router
	return deps, &handler
}

func TestHealthz(t *testing.T) {
	_, handler := setupTestRouter()
	req := httptest.NewRequest("GET", "/healthz", nil)
	w := httptest.NewRecorder()

	(*handler).ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected status 200, got %d", w.Code)
	}

	var resp map[string]bool
	json.NewDecoder(w.Body).Decode(&resp)
	if !resp["ok"] {
		t.Error("expected ok: true")
	}
}

func TestFamilyRoutes_Unauthenticated_Returns401(t *testing.T) {
	deps, handler := setupTestRouter()
	familyID := uuid.New()
	deps.MembershipStore.CreateFamily(familyID, "Test Family")

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String(), nil)
	w := httptest.NewRecorder()

	(*handler).ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("expected status 401, got %d", w.Code)
	}
}

func TestFamilyRoutes_NonMember_Returns404(t *testing.T) {
	deps, handler := setupTestRouter()
	familyID := uuid.New()
	userID := uuid.New()
	otherUserID := uuid.New()

	deps.MembershipStore.CreateFamily(familyID, "Test Family")
	deps.MembershipStore.AddMember(familyID, otherUserID)

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String(), nil)
	req.Header.Set("X-User-Id", userID.String())
	w := httptest.NewRecorder()

	(*handler).ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("expected status 404 for non-member, got %d", w.Code)
	}
}

func TestFamilyRoutes_WrongFamilyId_Returns404(t *testing.T) {
	deps, handler := setupTestRouter()
	familyID := uuid.New()
	wrongFamilyID := uuid.New()
	userID := uuid.New()

	deps.MembershipStore.CreateFamily(familyID, "Test Family")
	deps.MembershipStore.AddMember(familyID, userID)

	req := httptest.NewRequest("GET", "/api/v1/families/"+wrongFamilyID.String(), nil)
	req.Header.Set("X-User-Id", userID.String())
	w := httptest.NewRecorder()

	(*handler).ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("expected status 404 for wrong familyId, got %d", w.Code)
	}
}

func TestFamilyRoutes_InvalidFamilyId_Returns404(t *testing.T) {
	_, handler := setupTestRouter()
	userID := uuid.New()

	req := httptest.NewRequest("GET", "/api/v1/families/not-a-uuid", nil)
	req.Header.Set("X-User-Id", userID.String())
	w := httptest.NewRecorder()

	(*handler).ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("expected status 404 for invalid familyId, got %d", w.Code)
	}
}

func TestFamilyRoutes_Member_Returns200(t *testing.T) {
	deps, handler := setupTestRouter()
	familyID := uuid.New()
	userID := uuid.New()

	deps.MembershipStore.CreateFamily(familyID, "Test Family")
	deps.MembershipStore.AddMember(familyID, userID)

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String(), nil)
	req.Header.Set("X-User-Id", userID.String())
	w := httptest.NewRecorder()

	(*handler).ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected status 200 for member, got %d", w.Code)
	}

	var resp map[string]string
	json.NewDecoder(w.Body).Decode(&resp)
	if resp["id"] != familyID.String() {
		t.Errorf("expected family id %s, got %s", familyID.String(), resp["id"])
	}
	if resp["name"] != "Test Family" {
		t.Errorf("expected family name 'Test Family', got %s", resp["name"])
	}
}

func TestPersonsRoute_Member_ReturnsEmptyList(t *testing.T) {
	deps, handler := setupTestRouter()
	familyID := uuid.New()
	userID := uuid.New()

	deps.MembershipStore.CreateFamily(familyID, "Test Family")
	deps.MembershipStore.AddMember(familyID, userID)

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String()+"/persons", nil)
	req.Header.Set("X-User-Id", userID.String())
	w := httptest.NewRecorder()

	(*handler).ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected status 200, got %d", w.Code)
	}

	var resp map[string][]interface{}
	json.NewDecoder(w.Body).Decode(&resp)
	if len(resp["persons"]) != 0 {
		t.Errorf("expected empty persons list, got %d", len(resp["persons"]))
	}
}

func TestPersonsRoute_NonMember_Returns404(t *testing.T) {
	deps, handler := setupTestRouter()
	familyID := uuid.New()
	userID := uuid.New()

	deps.MembershipStore.CreateFamily(familyID, "Test Family")

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String()+"/persons", nil)
	req.Header.Set("X-User-Id", userID.String())
	w := httptest.NewRecorder()

	(*handler).ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("expected status 404 for non-member, got %d", w.Code)
	}
}

type testRouterDeps struct {
	membershipStore *family.InMemoryMembershipStore
	personStore     *person.InMemoryStore
	kinshipStore    *kinship.InMemoryStore
	handler         http.Handler
}

func setupTestRouterWithStores() *testRouterDeps {
	membershipStore := family.NewInMemoryMembershipStore()
	personStore := person.NewInMemoryStore()
	kinshipStore := kinship.NewInMemoryStore()
	relationshipService := app.NewRelationshipService(personStore, kinshipStore)

	router := httpapi.NewRouter(httpapi.RouterDeps{
		MembershipStore:     membershipStore,
		RelationshipService: relationshipService,
	})

	return &testRouterDeps{
		membershipStore: membershipStore,
		personStore:     personStore,
		kinshipStore:    kinshipStore,
		handler:         router,
	}
}

func TestRelationships_HappyPath(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	userID := uuid.New()
	parentID := uuid.New()
	childID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMember(familyID, userID)
	deps.personStore.Create(&person.Person{ID: parentID, FamilyID: familyID, FirstName: "Parent"})
	deps.personStore.Create(&person.Person{ID: childID, FamilyID: familyID, FirstName: "Child"})

	body := map[string]string{
		"parent_id":         parentID.String(),
		"child_id":          childID.String(),
		"relationship_type": "biological_father",
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/relationships", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", userID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusCreated {
		t.Errorf("expected status 201, got %d: %s", w.Code, w.Body.String())
	}
}

func TestRelationships_ThreeHopCycle_Rejected(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	userID := uuid.New()
	personA := uuid.New()
	personB := uuid.New()
	personC := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMember(familyID, userID)
	deps.personStore.Create(&person.Person{ID: personA, FamilyID: familyID, FirstName: "A"})
	deps.personStore.Create(&person.Person{ID: personB, FamilyID: familyID, FirstName: "B"})
	deps.personStore.Create(&person.Person{ID: personC, FamilyID: familyID, FirstName: "C"})

	addRelation := func(from, to uuid.UUID) int {
		body := map[string]string{
			"parent_id":         from.String(),
			"child_id":          to.String(),
			"relationship_type": "biological_father",
		}
		bodyBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/relationships", bytes.NewReader(bodyBytes))
		req.Header.Set("X-User-Id", userID.String())
		req.Header.Set("Content-Type", "application/json")
		w := httptest.NewRecorder()
		deps.handler.ServeHTTP(w, req)
		return w.Code
	}

	if code := addRelation(personA, personB); code != http.StatusCreated {
		t.Fatalf("A->B should succeed, got %d", code)
	}
	if code := addRelation(personB, personC); code != http.StatusCreated {
		t.Fatalf("B->C should succeed, got %d", code)
	}

	if code := addRelation(personC, personA); code != http.StatusUnprocessableEntity {
		t.Errorf("C->A (cycle) should return 422, got %d", code)
	}
}

func TestRelationships_DualBioFather_Rejected(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	userID := uuid.New()
	child := uuid.New()
	father1 := uuid.New()
	father2 := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMember(familyID, userID)
	deps.personStore.Create(&person.Person{ID: child, FamilyID: familyID, FirstName: "Child"})
	deps.personStore.Create(&person.Person{ID: father1, FamilyID: familyID, FirstName: "Father1"})
	deps.personStore.Create(&person.Person{ID: father2, FamilyID: familyID, FirstName: "Father2"})

	addRelation := func(parentID uuid.UUID) int {
		body := map[string]string{
			"parent_id":         parentID.String(),
			"child_id":          child.String(),
			"relationship_type": "biological_father",
		}
		bodyBytes, _ := json.Marshal(body)
		req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/relationships", bytes.NewReader(bodyBytes))
		req.Header.Set("X-User-Id", userID.String())
		req.Header.Set("Content-Type", "application/json")
		w := httptest.NewRecorder()
		deps.handler.ServeHTTP(w, req)
		return w.Code
	}

	if code := addRelation(father1); code != http.StatusCreated {
		t.Fatalf("first bio father should succeed, got %d", code)
	}

	if code := addRelation(father2); code != http.StatusUnprocessableEntity {
		t.Errorf("second bio father should return 422, got %d", code)
	}
}

func TestRelationships_PersonNotInFamily_Returns404(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	otherFamilyID := uuid.New()
	userID := uuid.New()
	parentID := uuid.New()
	childID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMember(familyID, userID)
	deps.personStore.Create(&person.Person{ID: parentID, FamilyID: familyID, FirstName: "Parent"})
	deps.personStore.Create(&person.Person{ID: childID, FamilyID: otherFamilyID, FirstName: "Child"})

	body := map[string]string{
		"parent_id":         parentID.String(),
		"child_id":          childID.String(),
		"relationship_type": "biological_father",
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/relationships", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", userID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("expected 404 when child not in family, got %d", w.Code)
	}
}
