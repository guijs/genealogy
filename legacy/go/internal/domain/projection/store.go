package projection

import (
	"sync"

	"github.com/google/uuid"
)

type Store interface {
	GetPerson(id uuid.UUID) (*Person, bool)
	GetPersonsByFamily(familyID uuid.UUID) []*Person
	GetMarriagesByFamily(familyID uuid.UUID) []*Marriage
	GetRelationshipsByFamily(familyID uuid.UUID) []*Relationship
	GetParentsOf(personID uuid.UUID) []*Relationship
	GetChildrenOf(personID uuid.UUID) []*Relationship
	GetMarriagesOf(personID uuid.UUID) []*Marriage

	CreatePerson(p *Person)
	CreateMarriage(m *Marriage)
	CreateRelationship(r *Relationship)
}

type InMemoryStore struct {
	mu            sync.RWMutex
	persons       map[uuid.UUID]*Person
	marriages     map[uuid.UUID]*Marriage
	relationships map[uuid.UUID]*Relationship
}

func NewInMemoryStore() *InMemoryStore {
	return &InMemoryStore{
		persons:       make(map[uuid.UUID]*Person),
		marriages:     make(map[uuid.UUID]*Marriage),
		relationships: make(map[uuid.UUID]*Relationship),
	}
}

func (s *InMemoryStore) GetPerson(id uuid.UUID) (*Person, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	p, ok := s.persons[id]
	if !ok {
		return nil, false
	}
	return &Person{
		ID:          p.ID,
		FamilyID:    p.FamilyID,
		DisplayName: p.DisplayName,
		Gender:      p.Gender,
		BirthYear:   p.BirthYear,
		DeathYear:   p.DeathYear,
		Hidden:      p.Hidden,
	}, true
}

func (s *InMemoryStore) GetPersonsByFamily(familyID uuid.UUID) []*Person {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var result []*Person
	for _, p := range s.persons {
		if p.FamilyID == familyID {
			result = append(result, &Person{
				ID:          p.ID,
				FamilyID:    p.FamilyID,
				DisplayName: p.DisplayName,
				Gender:      p.Gender,
				BirthYear:   p.BirthYear,
				DeathYear:   p.DeathYear,
				Hidden:      p.Hidden,
			})
		}
	}
	return result
}

func (s *InMemoryStore) GetMarriagesByFamily(familyID uuid.UUID) []*Marriage {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var result []*Marriage
	for _, m := range s.marriages {
		if m.FamilyID == familyID {
			result = append(result, &Marriage{
				ID:          m.ID,
				FamilyID:    m.FamilyID,
				Partner1ID:  m.Partner1ID,
				Partner2ID:  m.Partner2ID,
				Status:      m.Status,
				StartedAt:   m.StartedAt,
				EndedAt:     m.EndedAt,
				EndedReason: m.EndedReason,
			})
		}
	}
	return result
}

func (s *InMemoryStore) GetRelationshipsByFamily(familyID uuid.UUID) []*Relationship {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var result []*Relationship
	for _, r := range s.relationships {
		if r.FamilyID == familyID {
			result = append(result, &Relationship{
				ID:         r.ID,
				FamilyID:   r.FamilyID,
				ParentID:   r.ParentID,
				ChildID:    r.ChildID,
				Subtype:    r.Subtype,
				Role:       r.Role,
				MarriageID: r.MarriageID,
				Dissolved:  r.Dissolved,
			})
		}
	}
	return result
}

func (s *InMemoryStore) GetParentsOf(personID uuid.UUID) []*Relationship {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var result []*Relationship
	for _, r := range s.relationships {
		if r.ChildID == personID {
			result = append(result, &Relationship{
				ID:         r.ID,
				FamilyID:   r.FamilyID,
				ParentID:   r.ParentID,
				ChildID:    r.ChildID,
				Subtype:    r.Subtype,
				Role:       r.Role,
				MarriageID: r.MarriageID,
				Dissolved:  r.Dissolved,
			})
		}
	}
	return result
}

func (s *InMemoryStore) GetChildrenOf(personID uuid.UUID) []*Relationship {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var result []*Relationship
	for _, r := range s.relationships {
		if r.ParentID == personID {
			result = append(result, &Relationship{
				ID:         r.ID,
				FamilyID:   r.FamilyID,
				ParentID:   r.ParentID,
				ChildID:    r.ChildID,
				Subtype:    r.Subtype,
				Role:       r.Role,
				MarriageID: r.MarriageID,
				Dissolved:  r.Dissolved,
			})
		}
	}
	return result
}

func (s *InMemoryStore) GetMarriagesOf(personID uuid.UUID) []*Marriage {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var result []*Marriage
	for _, m := range s.marriages {
		if m.Partner1ID == personID || m.Partner2ID == personID {
			result = append(result, &Marriage{
				ID:          m.ID,
				FamilyID:    m.FamilyID,
				Partner1ID:  m.Partner1ID,
				Partner2ID:  m.Partner2ID,
				Status:      m.Status,
				StartedAt:   m.StartedAt,
				EndedAt:     m.EndedAt,
				EndedReason: m.EndedReason,
			})
		}
	}
	return result
}

func (s *InMemoryStore) CreatePerson(p *Person) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.persons[p.ID] = p
}

func (s *InMemoryStore) CreateMarriage(m *Marriage) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.marriages[m.ID] = m
}

func (s *InMemoryStore) CreateRelationship(r *Relationship) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.relationships[r.ID] = r
}
