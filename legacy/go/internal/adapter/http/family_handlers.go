package httpapi

import (
	"net/http"

	"github.com/guijs/genealogy/internal/domain/family"
)

type FamilyHandlers struct {
	membershipStore family.MembershipStore
}

func NewFamilyHandlers(store family.MembershipStore) *FamilyHandlers {
	return &FamilyHandlers{membershipStore: store}
}

type familyResponse struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

func (h *FamilyHandlers) GetFamily(w http.ResponseWriter, r *http.Request) {
	familyID, ok := GetFamilyID(r.Context())
	if !ok {
		writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
		return
	}

	fam, exists := h.membershipStore.GetFamily(familyID)
	if !exists {
		writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
		return
	}

	writeJSON(w, http.StatusOK, familyResponse{
		ID:   fam.ID.String(),
		Name: fam.Name,
	})
}

type personResponse struct {
	ID        string `json:"id"`
	FirstName string `json:"first_name"`
	LastName  string `json:"last_name"`
}

type personsListResponse struct {
	Persons []personResponse `json:"persons"`
}

func (h *FamilyHandlers) ListPersons(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, personsListResponse{Persons: []personResponse{}})
}
