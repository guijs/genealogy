package httpapi

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

func NewRouter() *chi.Mux {
	r := chi.NewRouter()

	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(middleware.RequestID)

	r.Get("/healthz", handleHealthz)

	// TODO: Family-scoped routes will be added under /api/v1/families/{familyId}/...
	// with RequireFamilyMember middleware for authorization.
	// See follow-up slices: B1 (Persons CRUD), B3 (Relationships), B4 (Graph query).

	return r
}

type healthResponse struct {
	OK bool `json:"ok"`
}

func handleHealthz(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(healthResponse{OK: true})
}
