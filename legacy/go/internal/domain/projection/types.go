package projection

import "github.com/google/uuid"

type Gender string

const (
	GenderMale        Gender = "male"
	GenderFemale      Gender = "female"
	GenderUnknown     Gender = "unknown"
	GenderUnspecified Gender = "unspecified"
)

type MarriageStatus string

const (
	MarriageActive   MarriageStatus = "active"
	MarriageDivorced MarriageStatus = "divorced"
	MarriageWidowed  MarriageStatus = "widowed"
	MarriageEnded    MarriageStatus = "ended"
)

type ParentChildSubtype string

const (
	SubtypeBiological ParentChildSubtype = "biological"
	SubtypeAdoptive   ParentChildSubtype = "adoptive"
)

type ParentRole string

const (
	RoleFather ParentRole = "father"
	RoleMother ParentRole = "mother"
	RoleParent ParentRole = "parent"
)

type PersonDTO struct {
	ID          string  `json:"id"`
	DisplayName string  `json:"displayName"`
	Gender      Gender  `json:"gender,omitempty"`
	BirthYear   *int    `json:"birthYear,omitempty"`
	DeathYear   *int    `json:"deathYear,omitempty"`
	Deceased    bool    `json:"deceased,omitempty"`
	Hidden      bool    `json:"-"`
}

type MarriageDTO struct {
	ID          string         `json:"id"`
	PartnerIDs  [2]string      `json:"partnerIds"`
	Status      MarriageStatus `json:"status"`
	StartedAt   *string        `json:"startedAt,omitempty"`
	EndedAt     *string        `json:"endedAt,omitempty"`
	EndedReason *string        `json:"endedReason,omitempty"`
}

type RelationshipDTO struct {
	ID         string             `json:"id"`
	Type       string             `json:"type"`
	Subtype    ParentChildSubtype `json:"subtype"`
	ParentID   string             `json:"parentId"`
	ChildID    string             `json:"childId"`
	Role       ParentRole         `json:"role,omitempty"`
	MarriageID *string            `json:"marriageId,omitempty"`
	Dissolved  bool               `json:"-"`
}

type GraphProjection struct {
	FamilyID       string            `json:"familyId"`
	RootPersonID   string            `json:"rootPersonId"`
	Depth          int               `json:"depth"`
	Truncated      bool              `json:"truncated"`
	TruncateReason *string           `json:"truncateReason,omitempty"`
	Persons        []PersonDTO       `json:"persons"`
	Marriages      []MarriageDTO     `json:"marriages"`
	Relationships  []RelationshipDTO `json:"relationships"`
}

type Person struct {
	ID          uuid.UUID
	FamilyID    uuid.UUID
	DisplayName string
	Gender      Gender
	BirthYear   *int
	DeathYear   *int
	Hidden      bool
}

type Marriage struct {
	ID          uuid.UUID
	FamilyID    uuid.UUID
	Partner1ID  uuid.UUID
	Partner2ID  uuid.UUID
	Status      MarriageStatus
	StartedAt   *string
	EndedAt     *string
	EndedReason *string
}

type Relationship struct {
	ID         uuid.UUID
	FamilyID   uuid.UUID
	ParentID   uuid.UUID
	ChildID    uuid.UUID
	Subtype    ParentChildSubtype
	Role       ParentRole
	MarriageID *uuid.UUID
	Dissolved  bool
}
