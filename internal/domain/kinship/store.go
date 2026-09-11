package kinship

import (
	"sync"

	"github.com/google/uuid"
)

type Store interface {
	GetGraphForFamily(familyID uuid.UUID) *Graph
	AddRelation(familyID uuid.UUID, rel Relation) error
}

type InMemoryStore struct {
	mu     sync.RWMutex
	graphs map[uuid.UUID]*Graph
}

func NewInMemoryStore() *InMemoryStore {
	return &InMemoryStore{
		graphs: make(map[uuid.UUID]*Graph),
	}
}

func (s *InMemoryStore) GetGraphForFamily(familyID uuid.UUID) *Graph {
	s.mu.Lock()
	defer s.mu.Unlock()
	g, ok := s.graphs[familyID]
	if !ok {
		g = NewGraph()
		s.graphs[familyID] = g
	}
	return g
}

func (s *InMemoryStore) AddRelation(familyID uuid.UUID, rel Relation) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	g, ok := s.graphs[familyID]
	if !ok {
		g = NewGraph()
		s.graphs[familyID] = g
	}
	return g.AddRelation(rel)
}
