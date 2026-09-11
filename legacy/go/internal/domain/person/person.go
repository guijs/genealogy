package person

import "github.com/google/uuid"

type ID = uuid.UUID

type Person struct {
	ID        ID
	FamilyID  uuid.UUID
	FirstName string
	LastName  string
}

func NewID() ID {
	return uuid.New()
}
