package app

import (
	"errors"

	"github.com/google/uuid"
	"github.com/guijs/genealogy/internal/domain/kinship"
	"github.com/guijs/genealogy/internal/domain/person"
)

var (
	ErrPersonNotInFamily = errors.New("person does not belong to this family")
	ErrInvalidRelationType = errors.New("invalid relationship type")
)

type RelationshipService struct {
	personStore  person.Store
	kinshipStore kinship.Store
}

func NewRelationshipService(personStore person.Store, kinshipStore kinship.Store) *RelationshipService {
	return &RelationshipService{
		personStore:  personStore,
		kinshipStore: kinshipStore,
	}
}

type AddParentChildRequest struct {
	FamilyID     uuid.UUID
	ParentID     uuid.UUID
	ChildID      uuid.UUID
	RelationType kinship.RelationType
}

func (s *RelationshipService) AddParentChild(req AddParentChildRequest) error {
	if !s.personStore.ExistsInFamily(req.ParentID, req.FamilyID) {
		return ErrPersonNotInFamily
	}
	if !s.personStore.ExistsInFamily(req.ChildID, req.FamilyID) {
		return ErrPersonNotInFamily
	}

	if !req.RelationType.IsParentRole() {
		return ErrInvalidRelationType
	}

	rel := kinship.Relation{
		From: req.ParentID,
		To:   req.ChildID,
		Type: req.RelationType,
	}

	return s.kinshipStore.AddRelation(req.FamilyID, rel)
}
