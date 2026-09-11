package family

import (
	"sync"

	"github.com/google/uuid"
)

type UserID = uuid.UUID

type MembershipStore interface {
	IsMember(familyID ID, userID UserID) bool
	AddMember(familyID ID, userID UserID)
	FamilyExists(familyID ID) bool
	CreateFamily(familyID ID, name string)
	GetFamily(familyID ID) (*Family, bool)
}

type InMemoryMembershipStore struct {
	mu         sync.RWMutex
	families   map[ID]*Family
	membership map[ID]map[UserID]bool
}

func NewInMemoryMembershipStore() *InMemoryMembershipStore {
	return &InMemoryMembershipStore{
		families:   make(map[ID]*Family),
		membership: make(map[ID]map[UserID]bool),
	}
}

func (s *InMemoryMembershipStore) CreateFamily(familyID ID, name string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.families[familyID] = &Family{ID: familyID, Name: name}
	s.membership[familyID] = make(map[UserID]bool)
}

func (s *InMemoryMembershipStore) FamilyExists(familyID ID) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	_, exists := s.families[familyID]
	return exists
}

func (s *InMemoryMembershipStore) GetFamily(familyID ID) (*Family, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	f, exists := s.families[familyID]
	if !exists {
		return nil, false
	}
	return &Family{ID: f.ID, Name: f.Name}, true
}

func (s *InMemoryMembershipStore) AddMember(familyID ID, userID UserID) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, exists := s.membership[familyID]; !exists {
		s.membership[familyID] = make(map[UserID]bool)
	}
	s.membership[familyID][userID] = true
}

func (s *InMemoryMembershipStore) IsMember(familyID ID, userID UserID) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	members, exists := s.membership[familyID]
	if !exists {
		return false
	}
	return members[userID]
}
