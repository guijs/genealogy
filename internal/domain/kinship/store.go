package kinship

import (
	"sync"

	"github.com/google/uuid"
)

type Store interface {
	GetGraphForFamily(familyID uuid.UUID) *Graph
	SaveRelation(familyID uuid.UUID, rel Relation)
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

func (s *InMemoryStore) SaveRelation(familyID uuid.UUID, rel Relation) {
	s.mu.Lock()
	defer s.mu.Unlock()
	g, ok := s.graphs[familyID]
	if !ok {
		g = NewGraph()
		s.graphs[familyID] = g
	}
	g.relations = append(g.relations, rel)
	g.children[rel.To] = append(g.children[rel.To], rel)
}
