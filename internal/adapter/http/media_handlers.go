package httpapi

import (
	"encoding/json"
	"errors"
	"net/http"

	"github.com/guijs/genealogy/internal/app"
)

type MediaHandlers struct {
	service *app.MediaService
}

func NewMediaHandlers(service *app.MediaService) *MediaHandlers {
	return &MediaHandlers{service: service}
}

type uploadURLRequest struct {
	MIMEType   string `json:"mime_type"`
	FileSize   int64  `json:"file_size"`
	StorageKey string `json:"storage_key,omitempty"`
}

type uploadURLResponse struct {
	UploadURL  string `json:"upload_url"`
	StorageKey string `json:"storage_key"`
}

func (h *MediaHandlers) GetUploadURL(w http.ResponseWriter, r *http.Request) {
	familyID, ok := GetFamilyID(r.Context())
	if !ok {
		writeJSON(w, http.StatusNotFound, errorResponse{Error: "not found"})
		return
	}

	var req uploadURLRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, errorResponse{Error: "invalid request body"})
		return
	}

	resp, err := h.service.GetUploadURL(app.UploadURLRequest{
		FamilyID:   familyID,
		MIMEType:   req.MIMEType,
		FileSize:   req.FileSize,
		StorageKey: req.StorageKey,
	})

	if err != nil {
		if errors.Is(err, app.ErrInvalidMIMEType) {
			writeJSON(w, http.StatusBadRequest, errorResponse{Error: err.Error()})
			return
		}
		if errors.Is(err, app.ErrFileTooLarge) {
			writeJSON(w, http.StatusBadRequest, errorResponse{Error: err.Error()})
			return
		}
		if errors.Is(err, app.ErrClientKeyRejected) {
			writeJSON(w, http.StatusBadRequest, errorResponse{Error: err.Error()})
			return
		}
		writeJSON(w, http.StatusInternalServerError, errorResponse{Error: "internal error"})
		return
	}

	writeJSON(w, http.StatusOK, uploadURLResponse{
		UploadURL:  resp.UploadURL,
		StorageKey: resp.StorageKey,
	})
}
