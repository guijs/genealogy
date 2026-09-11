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
	"github.com/guijs/genealogy/internal/domain/projection"
)

func setupTestRouter() (*httpapi.RouterDeps, *http.Handler) {
	membershipStore := family.NewInMemoryMembershipStore()
	personStore := person.NewInMemoryStore()
	kinshipStore := kinship.NewInMemoryStore()
	projectionStore := projection.NewInMemoryStore()
	objectStore := app.NewStubObjectStore()
	relationshipService := app.NewRelationshipService(personStore, kinshipStore)
	mediaService := app.NewMediaService(objectStore)
	graphService := app.NewGraphService(projectionStore)

	deps := &httpapi.RouterDeps{
		MembershipStore:     membershipStore,
		RelationshipService: relationshipService,
		MediaService:        mediaService,
		GraphService:        graphService,
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
	projectionStore *projection.InMemoryStore
	handler         http.Handler
}

func setupTestRouterWithStores() *testRouterDeps {
	membershipStore := family.NewInMemoryMembershipStore()
	personStore := person.NewInMemoryStore()
	kinshipStore := kinship.NewInMemoryStore()
	projectionStore := projection.NewInMemoryStore()
	objectStore := app.NewStubObjectStore()
	relationshipService := app.NewRelationshipService(personStore, kinshipStore)
	mediaService := app.NewMediaService(objectStore)
	graphService := app.NewGraphService(projectionStore)

	router := httpapi.NewRouter(httpapi.RouterDeps{
		MembershipStore:     membershipStore,
		RelationshipService: relationshipService,
		MediaService:        mediaService,
		GraphService:        graphService,
	})

	return &testRouterDeps{
		membershipStore: membershipStore,
		personStore:     personStore,
		kinshipStore:    kinshipStore,
		projectionStore: projectionStore,
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

// =============================================================================
// B3: Role Matrix Tests
// =============================================================================

func TestRBAC_AdminCanRead(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String(), nil)
	req.Header.Set("X-User-Id", adminID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("admin should read, expected 200, got %d", w.Code)
	}
}

func TestRBAC_AdminCanWrite(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()
	parentID := uuid.New()
	childID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)
	deps.personStore.Create(&person.Person{ID: parentID, FamilyID: familyID, FirstName: "Parent"})
	deps.personStore.Create(&person.Person{ID: childID, FamilyID: familyID, FirstName: "Child"})

	body := map[string]string{
		"parent_id":         parentID.String(),
		"child_id":          childID.String(),
		"relationship_type": "biological_father",
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/relationships", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", adminID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusCreated {
		t.Errorf("admin should write, expected 201, got %d: %s", w.Code, w.Body.String())
	}
}

func TestRBAC_ViewerCanRead(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	viewerID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, viewerID, family.RoleViewer)

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String(), nil)
	req.Header.Set("X-User-Id", viewerID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("viewer should read, expected 200, got %d", w.Code)
	}
}

func TestRBAC_ViewerCannotWrite_Returns403(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	viewerID := uuid.New()
	parentID := uuid.New()
	childID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, viewerID, family.RoleViewer)
	deps.personStore.Create(&person.Person{ID: parentID, FamilyID: familyID, FirstName: "Parent"})
	deps.personStore.Create(&person.Person{ID: childID, FamilyID: familyID, FirstName: "Child"})

	body := map[string]string{
		"parent_id":         parentID.String(),
		"child_id":          childID.String(),
		"relationship_type": "biological_father",
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/relationships", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", viewerID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusForbidden {
		t.Errorf("viewer should not write, expected 403, got %d", w.Code)
	}
}

func TestRBAC_NonMemberCannotRead_Returns404(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	nonMemberID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String(), nil)
	req.Header.Set("X-User-Id", nonMemberID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("non-member should get 404, got %d", w.Code)
	}
}

func TestRBAC_NonMemberCannotWrite_Returns404(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	nonMemberID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")

	body := map[string]string{
		"parent_id":         uuid.New().String(),
		"child_id":          uuid.New().String(),
		"relationship_type": "biological_father",
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/relationships", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", nonMemberID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("non-member write should get 404 (not 403), got %d", w.Code)
	}
}

// =============================================================================
// B4: Media Upload Safety Tests
// =============================================================================

func TestMediaUpload_HappyPath(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)

	body := map[string]interface{}{
		"mime_type": "image/jpeg",
		"file_size": 1024,
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/media/upload-url", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", adminID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d: %s", w.Code, w.Body.String())
	}

	var resp map[string]string
	json.NewDecoder(w.Body).Decode(&resp)
	if resp["upload_url"] == "" {
		t.Error("expected upload_url in response")
	}
	if resp["storage_key"] == "" {
		t.Error("expected storage_key in response")
	}
}

func TestMediaUpload_BadMIME_Returns400(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)

	body := map[string]interface{}{
		"mime_type": "application/pdf",
		"file_size": 1024,
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/media/upload-url", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", adminID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("bad MIME should return 400, got %d", w.Code)
	}
}

func TestMediaUpload_Oversized_Returns400(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)

	body := map[string]interface{}{
		"mime_type": "image/jpeg",
		"file_size": 10 * 1024 * 1024, // 10MB > 5MB limit
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/media/upload-url", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", adminID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("oversized file should return 400, got %d", w.Code)
	}
}

func TestMediaUpload_ClientKey_Returns400(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)

	body := map[string]interface{}{
		"mime_type":   "image/jpeg",
		"file_size":   1024,
		"storage_key": "malicious/path/to/overwrite.jpg",
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/media/upload-url", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", adminID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("client-supplied key should return 400, got %d", w.Code)
	}
}

func TestMediaUpload_NonMember_Returns404(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	nonMemberID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")

	body := map[string]interface{}{
		"mime_type": "image/jpeg",
		"file_size": 1024,
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/media/upload-url", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", nonMemberID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("non-member should get 404, got %d", w.Code)
	}
}

func TestMediaUpload_ViewerCannotUpload_Returns403(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	viewerID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, viewerID, family.RoleViewer)

	body := map[string]interface{}{
		"mime_type": "image/jpeg",
		"file_size": 1024,
	}
	bodyBytes, _ := json.Marshal(body)

	req := httptest.NewRequest("POST", "/api/v1/families/"+familyID.String()+"/media/upload-url", bytes.NewReader(bodyBytes))
	req.Header.Set("X-User-Id", viewerID.String())
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusForbidden {
		t.Errorf("viewer should get 403 for upload, got %d", w.Code)
	}
}

// =============================================================================
// Graph Endpoint Tests
// =============================================================================

func TestGraph_NonMember_Returns404(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	nonMemberID := uuid.New()
	rootPersonID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          rootPersonID,
		FamilyID:    familyID,
		DisplayName: "Root Person",
	})

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String()+"/graph?rootPersonId="+rootPersonID.String(), nil)
	req.Header.Set("X-User-Id", nonMemberID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("non-member should get 404, got %d", w.Code)
	}
}

func TestGraph_DepthClamp_MaxIs8(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()
	rootPersonID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          rootPersonID,
		FamilyID:    familyID,
		DisplayName: "Root Person",
	})

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String()+"/graph?rootPersonId="+rootPersonID.String()+"&depth=20", nil)
	req.Header.Set("X-User-Id", adminID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d: %s", w.Code, w.Body.String())
	}

	var resp map[string]interface{}
	json.NewDecoder(w.Body).Decode(&resp)
	depth := int(resp["depth"].(float64))
	if depth != 8 {
		t.Errorf("depth should be clamped to 8, got %d", depth)
	}
}

func TestGraph_DefaultDepth_Is3(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()
	rootPersonID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          rootPersonID,
		FamilyID:    familyID,
		DisplayName: "Root Person",
	})

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String()+"/graph?rootPersonId="+rootPersonID.String(), nil)
	req.Header.Set("X-User-Id", adminID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d: %s", w.Code, w.Body.String())
	}

	var resp map[string]interface{}
	json.NewDecoder(w.Body).Decode(&resp)
	depth := int(resp["depth"].(float64))
	if depth != 3 {
		t.Errorf("default depth should be 3, got %d", depth)
	}
}

func TestGraph_IncludesEndedMarriage(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()
	person1ID := uuid.New()
	person2ID := uuid.New()
	marriageID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          person1ID,
		FamilyID:    familyID,
		DisplayName: "Person 1",
	})
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          person2ID,
		FamilyID:    familyID,
		DisplayName: "Person 2",
	})
	reason := "irreconcilable differences"
	deps.projectionStore.CreateMarriage(&projection.Marriage{
		ID:          marriageID,
		FamilyID:    familyID,
		Partner1ID:  person1ID,
		Partner2ID:  person2ID,
		Status:      projection.MarriageDivorced,
		EndedReason: &reason,
	})

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String()+"/graph?rootPersonId="+person1ID.String(), nil)
	req.Header.Set("X-User-Id", adminID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d: %s", w.Code, w.Body.String())
	}

	var resp map[string]interface{}
	json.NewDecoder(w.Body).Decode(&resp)

	marriages := resp["marriages"].([]interface{})
	if len(marriages) != 1 {
		t.Fatalf("expected 1 marriage (ended), got %d", len(marriages))
	}

	marriage := marriages[0].(map[string]interface{})
	if marriage["status"] != "divorced" {
		t.Errorf("expected status 'divorced', got %v", marriage["status"])
	}
	if marriage["endedReason"] != "irreconcilable differences" {
		t.Errorf("expected endedReason, got %v", marriage["endedReason"])
	}
}

func TestGraph_ExcludesDissolvedRelationship(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()
	parentID := uuid.New()
	childID := uuid.New()
	relationshipID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          parentID,
		FamilyID:    familyID,
		DisplayName: "Parent",
	})
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          childID,
		FamilyID:    familyID,
		DisplayName: "Child",
	})
	deps.projectionStore.CreateRelationship(&projection.Relationship{
		ID:        relationshipID,
		FamilyID:  familyID,
		ParentID:  parentID,
		ChildID:   childID,
		Subtype:   projection.SubtypeBiological,
		Role:      projection.RoleFather,
		Dissolved: true,
	})

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String()+"/graph?rootPersonId="+parentID.String(), nil)
	req.Header.Set("X-User-Id", adminID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d: %s", w.Code, w.Body.String())
	}

	var resp map[string]interface{}
	json.NewDecoder(w.Body).Decode(&resp)

	relationships := resp["relationships"].([]interface{})
	if len(relationships) != 0 {
		t.Errorf("dissolved relationship should be excluded, got %d relationships", len(relationships))
	}
}

func TestGraph_ExcludesHiddenPerson(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	adminID := uuid.New()
	visibleID := uuid.New()
	hiddenID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          visibleID,
		FamilyID:    familyID,
		DisplayName: "Visible Person",
		Hidden:      false,
	})
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          hiddenID,
		FamilyID:    familyID,
		DisplayName: "Hidden Person",
		Hidden:      true,
	})

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String()+"/graph?rootPersonId="+visibleID.String(), nil)
	req.Header.Set("X-User-Id", adminID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("expected 200, got %d: %s", w.Code, w.Body.String())
	}

	var resp map[string]interface{}
	json.NewDecoder(w.Body).Decode(&resp)

	persons := resp["persons"].([]interface{})
	for _, p := range persons {
		person := p.(map[string]interface{})
		if person["displayName"] == "Hidden Person" {
			t.Error("hidden person should be excluded from graph")
		}
	}
}

func TestGraph_RootPersonNotInFamily_Returns404(t *testing.T) {
	deps := setupTestRouterWithStores()
	familyID := uuid.New()
	otherFamilyID := uuid.New()
	adminID := uuid.New()
	rootPersonID := uuid.New()

	deps.membershipStore.CreateFamily(familyID, "Test Family")
	deps.membershipStore.AddMemberWithRole(familyID, adminID, family.RoleAdmin)
	deps.projectionStore.CreatePerson(&projection.Person{
		ID:          rootPersonID,
		FamilyID:    otherFamilyID,
		DisplayName: "Other Family Person",
	})

	req := httptest.NewRequest("GET", "/api/v1/families/"+familyID.String()+"/graph?rootPersonId="+rootPersonID.String(), nil)
	req.Header.Set("X-User-Id", adminID.String())
	w := httptest.NewRecorder()

	deps.handler.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Errorf("expected 404 for root person in different family, got %d", w.Code)
	}
}
