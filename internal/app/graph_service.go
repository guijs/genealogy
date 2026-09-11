package app

import (
	"github.com/google/uuid"
	"github.com/guijs/genealogy/internal/domain/projection"
)

const (
	DefaultDepth = 3
	MaxDepth     = 8
)

type GraphService struct {
	projectionStore projection.Store
}

func NewGraphService(projectionStore projection.Store) *GraphService {
	return &GraphService{projectionStore: projectionStore}
}

type GetGraphRequest struct {
	FamilyID     uuid.UUID
	RootPersonID uuid.UUID
	Depth        int
}

func (s *GraphService) GetGraph(req GetGraphRequest) (*projection.GraphProjection, error) {
	depth := req.Depth
	if depth <= 0 {
		depth = DefaultDepth
	}

	root, ok := s.projectionStore.GetPerson(req.RootPersonID)
	if !ok || root.FamilyID != req.FamilyID {
		return nil, ErrPersonNotInFamily
	}

	visited := make(map[uuid.UUID]bool)
	persons := make(map[uuid.UUID]*projection.Person)
	marriages := make(map[uuid.UUID]*projection.Marriage)
	relationships := make(map[uuid.UUID]*projection.Relationship)
	truncated := false

	s.traverse(req.RootPersonID, req.FamilyID, depth, 0, visited, persons, marriages, relationships, &truncated)

	personDTOs := make([]projection.PersonDTO, 0)
	for _, p := range persons {
		if p.Hidden {
			continue
		}
		personDTOs = append(personDTOs, projection.PersonDTO{
			ID:          p.ID.String(),
			DisplayName: p.DisplayName,
			Gender:      p.Gender,
			BirthYear:   p.BirthYear,
			DeathYear:   p.DeathYear,
			Deceased:    p.DeathYear != nil,
		})
	}

	marriageDTOs := make([]projection.MarriageDTO, 0)
	for _, m := range marriages {
		marriageDTOs = append(marriageDTOs, projection.MarriageDTO{
			ID:          m.ID.String(),
			PartnerIDs:  [2]string{m.Partner1ID.String(), m.Partner2ID.String()},
			Status:      m.Status,
			StartedAt:   m.StartedAt,
			EndedAt:     m.EndedAt,
			EndedReason: m.EndedReason,
		})
	}

	relationshipDTOs := make([]projection.RelationshipDTO, 0)
	for _, r := range relationships {
		if r.Dissolved {
			continue
		}
		parentPerson, _ := s.projectionStore.GetPerson(r.ParentID)
		childPerson, _ := s.projectionStore.GetPerson(r.ChildID)
		if parentPerson != nil && parentPerson.Hidden {
			continue
		}
		if childPerson != nil && childPerson.Hidden {
			continue
		}

		var marriageID *string
		if r.MarriageID != nil {
			mid := r.MarriageID.String()
			marriageID = &mid
		}
		relationshipDTOs = append(relationshipDTOs, projection.RelationshipDTO{
			ID:         r.ID.String(),
			Type:       "PARENT_CHILD",
			Subtype:    r.Subtype,
			ParentID:   r.ParentID.String(),
			ChildID:    r.ChildID.String(),
			Role:       r.Role,
			MarriageID: marriageID,
		})
	}

	var truncateReason *string
	if truncated {
		reason := "已达展开上限"
		truncateReason = &reason
	}

	return &projection.GraphProjection{
		FamilyID:       req.FamilyID.String(),
		RootPersonID:   req.RootPersonID.String(),
		Depth:          depth,
		Truncated:      truncated,
		TruncateReason: truncateReason,
		Persons:        personDTOs,
		Marriages:      marriageDTOs,
		Relationships:  relationshipDTOs,
	}, nil
}

func (s *GraphService) traverse(
	personID uuid.UUID,
	familyID uuid.UUID,
	maxDepth int,
	currentDepth int,
	visited map[uuid.UUID]bool,
	persons map[uuid.UUID]*projection.Person,
	marriages map[uuid.UUID]*projection.Marriage,
	relationships map[uuid.UUID]*projection.Relationship,
	truncated *bool,
) {
	if currentDepth > maxDepth {
		return
	}
	if visited[personID] {
		return
	}
	visited[personID] = true

	person, ok := s.projectionStore.GetPerson(personID)
	if !ok || person.FamilyID != familyID {
		return
	}
	persons[personID] = person

	personMarriages := s.projectionStore.GetMarriagesOf(personID)
	for _, m := range personMarriages {
		if m.FamilyID != familyID {
			continue
		}
		marriages[m.ID] = m

		var spouseID uuid.UUID
		if m.Partner1ID == personID {
			spouseID = m.Partner2ID
		} else {
			spouseID = m.Partner1ID
		}
		if !visited[spouseID] {
			spouse, ok := s.projectionStore.GetPerson(spouseID)
			if ok && spouse.FamilyID == familyID {
				persons[spouseID] = spouse
				visited[spouseID] = true
			}
		}
	}

	parentRels := s.projectionStore.GetParentsOf(personID)
	for _, r := range parentRels {
		if r.FamilyID != familyID || r.Dissolved {
			continue
		}
		relationships[r.ID] = r
		if currentDepth+1 > maxDepth && !visited[r.ParentID] {
			*truncated = true
		} else {
			s.traverse(r.ParentID, familyID, maxDepth, currentDepth+1, visited, persons, marriages, relationships, truncated)
		}
	}

	childRels := s.projectionStore.GetChildrenOf(personID)
	for _, r := range childRels {
		if r.FamilyID != familyID || r.Dissolved {
			continue
		}
		relationships[r.ID] = r
		if currentDepth+1 > maxDepth && !visited[r.ChildID] {
			*truncated = true
		} else {
			s.traverse(r.ChildID, familyID, maxDepth, currentDepth+1, visited, persons, marriages, relationships, truncated)
		}
	}
}
