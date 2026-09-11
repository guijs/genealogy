package family

import "github.com/google/uuid"

type ID = uuid.UUID

type Family struct {
	ID   ID
	Name string
}

func NewID() ID {
	return uuid.New()
}
