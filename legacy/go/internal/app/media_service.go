package app

import (
	"errors"
	"strings"

	"github.com/google/uuid"
)

var (
	ErrInvalidMIMEType   = errors.New("invalid MIME type: only jpeg, png, webp allowed")
	ErrFileTooLarge      = errors.New("file too large: maximum 5MB allowed")
	ErrClientKeyRejected = errors.New("client-supplied storage key not allowed")
)

const MaxMediaSize = 5 * 1024 * 1024 // 5MB

var AllowedMIMETypes = map[string]bool{
	"image/jpeg": true,
	"image/png":  true,
	"image/webp": true,
}

type ObjectStore interface {
	GenerateUploadURL(key string, mimeType string, maxSize int64) (string, error)
}

type StubObjectStore struct{}

func NewStubObjectStore() *StubObjectStore {
	return &StubObjectStore{}
}

func (s *StubObjectStore) GenerateUploadURL(key string, mimeType string, maxSize int64) (string, error) {
	return "https://storage.example.com/upload?key=" + key, nil
}

type MediaService struct {
	objectStore ObjectStore
}

func NewMediaService(objectStore ObjectStore) *MediaService {
	return &MediaService{objectStore: objectStore}
}

type UploadURLRequest struct {
	FamilyID   uuid.UUID
	MIMEType   string
	FileSize   int64
	StorageKey string
}

type UploadURLResponse struct {
	UploadURL  string
	StorageKey string
}

func (s *MediaService) GetUploadURL(req UploadURLRequest) (*UploadURLResponse, error) {
	if req.StorageKey != "" {
		return nil, ErrClientKeyRejected
	}

	mimeType := strings.ToLower(strings.TrimSpace(req.MIMEType))
	if !AllowedMIMETypes[mimeType] {
		return nil, ErrInvalidMIMEType
	}

	if req.FileSize > MaxMediaSize {
		return nil, ErrFileTooLarge
	}

	storageKey := generateStorageKey(req.FamilyID, mimeType)

	uploadURL, err := s.objectStore.GenerateUploadURL(storageKey, mimeType, MaxMediaSize)
	if err != nil {
		return nil, err
	}

	return &UploadURLResponse{
		UploadURL:  uploadURL,
		StorageKey: storageKey,
	}, nil
}

func generateStorageKey(familyID uuid.UUID, mimeType string) string {
	ext := ".bin"
	switch mimeType {
	case "image/jpeg":
		ext = ".jpg"
	case "image/png":
		ext = ".png"
	case "image/webp":
		ext = ".webp"
	}
	return "families/" + familyID.String() + "/media/" + uuid.New().String() + ext
}
