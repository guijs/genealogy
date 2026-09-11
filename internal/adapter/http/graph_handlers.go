package httpapi

import (
	"errors"
	"net/http"
	"strconv"

	"github.com/google/uuid"
	"github.com/guijs/genealogy/internal/app"
)

type GraphHandlers struct {
	service *app.GraphService
}

func NewGraphHandlers(service *app.GraphService) *GraphHandlers {
	return &GraphHandlers{service: service}
}

func (h *GraphHandlers) GetGraph(w http.ResponseWriter, r *http.Request) {
	familyID, ok := GetFamilyID(r.Context())
	if !ok {
		writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
		return
	}

	rootPersonIDStr := r.URL.Query().Get("rootPersonId")
	if rootPersonIDStr == "" {
		writeJSON(w, http.StatusBadRequest, errorResponse{Error: "rootPersonId query parameter required"})
		return
	}

	rootPersonID, err := uuid.Parse(rootPersonIDStr)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, errorResponse{Error: "invalid rootPersonId"})
		return
	}

	depth := app.DefaultDepth
	depthStr := r.URL.Query().Get("depth")
	if depthStr != "" {
		parsedDepth, err := strconv.Atoi(depthStr)
		if err == nil && parsedDepth > 0 {
			depth = parsedDepth
		}
	}

	graph, err := h.service.GetGraph(app.GetGraphRequest{
		FamilyID:     familyID,
		RootPersonID: rootPersonID,
		Depth:        depth,
	})

	if err != nil {
		if errors.Is(err, app.ErrPersonNotInFamily) {
			writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
			return
		}
		writeJSON(w, http.StatusInternalServerError, errorResponse{Error: "internal error"})
		return
	}

	writeJSON(w, http.StatusOK, graph)
}
