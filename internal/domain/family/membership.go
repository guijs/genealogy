package family

import (
	"sync"

	"github.com/google/uuid"
)

type UserID = uuid.UUID

type Membership struct {
	UserID UserID
	Role   Role
}

type MembershipStore interface {
	IsMember(familyID ID, userID UserID) bool
	GetMembership(familyID ID, userID UserID) (*Membership, bool)
	AddMember(familyID ID, userID UserID)
	AddMemberWithRole(familyID ID, userID UserID, role Role)
	FamilyExists(familyID ID) bool
	CreateFamily(familyID ID, name string)
	GetFamily(familyID ID) (*Family, bool)
}

type InMemoryMembershipStore struct {
	mu         sync.RWMutex
	families   map[ID]*Family
	membership map[ID]map[UserID]*Membership
}

func NewInMemoryMembershipStore() *InMemoryMembershipStore {
	return &InMemoryMembershipStore{
		families:   make(map[ID]*Family),
		membership: make(map[ID]map[UserID]*Membership),
	}
}

func (s *InMemoryMembershipStore) CreateFamily(familyID ID, name string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.families[familyID] = &Family{ID: familyID, Name: name}
	s.membership[familyID] = make(map[UserID]*Membership)
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
	s.AddMemberWithRole(familyID, userID, RoleAdmin)
}

func (s *InMemoryMembershipStore) AddMemberWithRole(familyID ID, userID UserID, role Role) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, exists := s.membership[familyID]; !exists {
		s.membership[familyID] = make(map[UserID]*Membership)
	}
	s.membership[familyID][userID] = &Membership{UserID: userID, Role: role}
}

func (s *InMemoryMembershipStore) IsMember(familyID ID, userID UserID) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	members, exists := s.membership[familyID]
	if !exists {
		return false
	}
	_, isMember := members[userID]
	return isMember
}

func (s *InMemoryMembershipStore) GetMembership(familyID ID, userID UserID) (*Membership, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	members, exists := s.membership[familyID]
	if !exists {
		return nil, false
	}
	m, ok := members[userID]
	if !ok {
		return nil, false
	}
	return &Membership{UserID: m.UserID, Role: m.Role}, true
}
