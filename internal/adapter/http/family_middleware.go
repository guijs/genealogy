package httpapi

import (
	"context"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/guijs/genealogy/internal/domain/family"
)

const (
	FamilyIDContextKey contextKey = "familyID"
)

type FamilyMiddleware struct {
	membershipStore family.MembershipStore
}

func NewFamilyMiddleware(store family.MembershipStore) *FamilyMiddleware {
	return &FamilyMiddleware{membershipStore: store}
}

func (m *FamilyMiddleware) RequireFamilyMember(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		familyIDStr := chi.URLParam(r, "familyId")
		if familyIDStr == "" {
			writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
			return
		}

		familyID, err := uuid.Parse(familyIDStr)
		if err != nil {
			writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
			return
		}

		if !m.membershipStore.FamilyExists(familyID) {
			writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
			return
		}

		userID, ok := GetUserID(r.Context())
		if !ok {
			writeJSON(w, http.StatusUnauthorized, errorResponse{Error: "authentication required"})
			return
		}

		if !m.membershipStore.IsMember(familyID, userID) {
			writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
			return
		}

		ctx := context.WithValue(r.Context(), FamilyIDContextKey, familyID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func GetFamilyID(ctx context.Context) (uuid.UUID, bool) {
	familyID, ok := ctx.Value(FamilyIDContextKey).(uuid.UUID)
	return familyID, ok
}
