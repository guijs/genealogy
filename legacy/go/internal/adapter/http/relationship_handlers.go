package httpapi

import (
	"encoding/json"
	"errors"
	"net/http"

	"github.com/google/uuid"
	"github.com/guijs/genealogy/internal/app"
	"github.com/guijs/genealogy/internal/domain/kinship"
)

type RelationshipHandlers struct {
	service *app.RelationshipService
}

func NewRelationshipHandlers(service *app.RelationshipService) *RelationshipHandlers {
	return &RelationshipHandlers{service: service}
}

type addRelationshipRequest struct {
	ParentID     string `json:"parent_id"`
	ChildID      string `json:"child_id"`
	RelationType string `json:"relationship_type"`
}

type addRelationshipResponse struct {
	Success bool `json:"success"`
}

func (h *RelationshipHandlers) AddRelationship(w http.ResponseWriter, r *http.Request) {
	familyID, ok := GetFamilyID(r.Context())
	if !ok {
		writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
		return
	}

	var req addRelationshipRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, errorResponse{Error: "invalid request body"})
		return
	}

	parentID, err := uuid.Parse(req.ParentID)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, errorResponse{Error: "invalid parent_id"})
		return
	}

	childID, err := uuid.Parse(req.ChildID)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, errorResponse{Error: "invalid child_id"})
		return
	}

	relType := kinship.RelationType(req.RelationType)
	if !relType.IsParentRole() {
		writeJSON(w, http.StatusBadRequest, errorResponse{Error: "invalid relationship_type"})
		return
	}

	err = h.service.AddParentChild(app.AddParentChildRequest{
		FamilyID:     familyID,
		ParentID:     parentID,
		ChildID:      childID,
		RelationType: relType,
	})

	if err != nil {
		if errors.Is(err, app.ErrPersonNotInFamily) {
			writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
			return
		}
		if errors.Is(err, kinship.ErrCycleDetected) {
			writeJSON(w, http.StatusUnprocessableEntity, errorResponse{Error: "relationship would create a cycle"})
			return
		}
		if errors.Is(err, kinship.ErrDualBiologicalFather) ||
			errors.Is(err, kinship.ErrDualBiologicalMother) ||
			errors.Is(err, kinship.ErrDualAdoptiveFather) ||
			errors.Is(err, kinship.ErrDualAdoptiveMother) {
			writeJSON(w, http.StatusUnprocessableEntity, errorResponse{Error: err.Error()})
			return
		}
		if errors.Is(err, kinship.ErrSelfLoop) {
			writeJSON(w, http.StatusUnprocessableEntity, errorResponse{Error: "person cannot be their own parent"})
			return
		}
		writeJSON(w, http.StatusInternalServerError, errorResponse{Error: "internal error"})
		return
	}

	writeJSON(w, http.StatusCreated, addRelationshipResponse{Success: true})
}
