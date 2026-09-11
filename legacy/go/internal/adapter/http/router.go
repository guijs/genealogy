package httpapi

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/guijs/genealogy/internal/app"
	"github.com/guijs/genealogy/internal/domain/family"
)

type RouterDeps struct {
	MembershipStore     family.MembershipStore
	RelationshipService *app.RelationshipService
	MediaService        *app.MediaService
	GraphService        *app.GraphService
}

func NewRouter(deps RouterDeps) *chi.Mux {
	r := chi.NewRouter()

	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(middleware.RequestID)

	r.Get("/healthz", handleHealthz)

	familyMiddleware := NewFamilyMiddleware(deps.MembershipStore)
	familyHandlers := NewFamilyHandlers(deps.MembershipStore)
	relationshipHandlers := NewRelationshipHandlers(deps.RelationshipService)
	mediaHandlers := NewMediaHandlers(deps.MediaService)
	graphHandlers := NewGraphHandlers(deps.GraphService)

	r.Route("/api/v1", func(r chi.Router) {
		r.Route("/families/{familyId}", func(r chi.Router) {
			r.Use(RequireAuth)
			r.Use(familyMiddleware.RequireFamilyMember)

			r.Get("/", familyHandlers.GetFamily)
			r.Get("/persons", familyHandlers.ListPersons)
			r.Get("/graph", graphHandlers.GetGraph)

			r.Group(func(r chi.Router) {
				r.Use(familyMiddleware.RequireWriteAccess)
				r.Post("/relationships", relationshipHandlers.AddRelationship)
				r.Post("/media/upload-url", mediaHandlers.GetUploadURL)
			})
		})
	})

	return r
}

type healthResponse struct {
	OK bool `json:"ok"`
}

func handleHealthz(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, healthResponse{OK: true})
}
