package httpapi

import (
	"context"
	"net/http"

	"github.com/google/uuid"
)

type contextKey string

const (
	UserIDContextKey contextKey = "userID"
)

func RequireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		userIDStr := r.Header.Get("X-User-Id")
		if userIDStr == "" {
			writeJSON(w, http.StatusUnauthorized, errorResponse{Error: "authentication required"})
			return
		}

		userID, err := uuid.Parse(userIDStr)
		if err != nil {
			writeJSON(w, http.StatusUnauthorized, errorResponse{Error: "invalid user id"})
			return
		}

		ctx := context.WithValue(r.Context(), UserIDContextKey, userID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func GetUserID(ctx context.Context) (uuid.UUID, bool) {
	userID, ok := ctx.Value(UserIDContextKey).(uuid.UUID)
	return userID, ok
}
