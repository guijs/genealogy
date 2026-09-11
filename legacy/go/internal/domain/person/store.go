package person

import (
	"sync"

	"github.com/google/uuid"
)

type Store interface {
	Get(id ID) (*Person, bool)
	Create(p *Person)
	ListByFamily(familyID uuid.UUID) []*Person
	ExistsInFamily(personID ID, familyID uuid.UUID) bool
}

type InMemoryStore struct {
	mu      sync.RWMutex
	persons map[ID]*Person
}

func NewInMemoryStore() *InMemoryStore {
	return &InMemoryStore{
		persons: make(map[ID]*Person),
	}
}

func (s *InMemoryStore) Get(id ID) (*Person, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	p, ok := s.persons[id]
	if !ok {
		return nil, false
	}
	return &Person{
		ID:        p.ID,
		FamilyID:  p.FamilyID,
		FirstName: p.FirstName,
		LastName:  p.LastName,
	}, true
}

func (s *InMemoryStore) Create(p *Person) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.persons[p.ID] = p
}

func (s *InMemoryStore) ListByFamily(familyID uuid.UUID) []*Person {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var result []*Person
	for _, p := range s.persons {
		if p.FamilyID == familyID {
			result = append(result, &Person{
				ID:        p.ID,
				FamilyID:  p.FamilyID,
				FirstName: p.FirstName,
				LastName:  p.LastName,
			})
		}
	}
	return result
}

func (s *InMemoryStore) ExistsInFamily(personID ID, familyID uuid.UUID) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	p, ok := s.persons[personID]
	if !ok {
		return false
	}
	return p.FamilyID == familyID
}
