package app

import (
	"testing"

	"github.com/google/uuid"
	"github.com/guijs/genealogy/internal/domain/projection"
)

func TestGetGraph_NoDanglingEdgesWhenTruncated(t *testing.T) {
	store := projection.NewInMemoryStore()
	familyID := uuid.New()

	// Create a chain: root -> gen1 -> gen2 -> gen3 -> gen4 (5 generations)
	// With depth=2, we should only see root, gen1, gen2
	// gen3 and gen4 should be truncated out, along with their relationships

	root := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Root"}
	gen1 := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Gen1"}
	gen2 := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Gen2"}
	gen3 := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Gen3"}
	gen4 := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Gen4"}

	store.CreatePerson(root)
	store.CreatePerson(gen1)
	store.CreatePerson(gen2)
	store.CreatePerson(gen3)
	store.CreatePerson(gen4)

	// root is parent of gen1
	rel1 := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: root.ID,
		ChildID:  gen1.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}
	// gen1 is parent of gen2
	rel2 := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: gen1.ID,
		ChildID:  gen2.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}
	// gen2 is parent of gen3
	rel3 := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: gen2.ID,
		ChildID:  gen3.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}
	// gen3 is parent of gen4
	rel4 := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: gen3.ID,
		ChildID:  gen4.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}

	store.CreateRelationship(rel1)
	store.CreateRelationship(rel2)
	store.CreateRelationship(rel3)
	store.CreateRelationship(rel4)

	service := NewGraphService(store)

	graph, err := service.GetGraph(GetGraphRequest{
		FamilyID:     familyID,
		RootPersonID: root.ID,
		Depth:        2,
	})

	if err != nil {
		t.Fatalf("GetGraph failed: %v", err)
	}

	if !graph.Truncated {
		t.Error("expected truncated=true, got false")
	}

	if graph.TruncateReason == nil || *graph.TruncateReason == "" {
		t.Error("expected truncateReason to be set")
	}

	// Build a set of person IDs in the response
	personIDs := make(map[string]bool)
	for _, p := range graph.Persons {
		personIDs[p.ID] = true
	}

	// Verify no relationship references a missing person
	for _, r := range graph.Relationships {
		if !personIDs[r.ParentID] {
			t.Errorf("relationship %s references missing parent %s", r.ID, r.ParentID)
		}
		if !personIDs[r.ChildID] {
			t.Errorf("relationship %s references missing child %s", r.ID, r.ChildID)
		}
	}

	// Verify no marriage references a missing person
	for _, m := range graph.Marriages {
		if !personIDs[m.PartnerIDs[0]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[0])
		}
		if !personIDs[m.PartnerIDs[1]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[1])
		}
	}

	// Verify we have expected persons (root, gen1, gen2)
	expectedPersonCount := 3 // root, gen1, gen2
	if len(graph.Persons) != expectedPersonCount {
		t.Errorf("expected %d persons, got %d", expectedPersonCount, len(graph.Persons))
	}

	// Verify we have expected relationships (rel1, rel2 only - not rel3 because gen3 is truncated)
	expectedRelCount := 2 // rel1, rel2
	if len(graph.Relationships) != expectedRelCount {
		t.Errorf("expected %d relationships, got %d", expectedRelCount, len(graph.Relationships))
	}
}

func TestGetGraph_NoDanglingMarriageWhenPartnerTruncated(t *testing.T) {
	store := projection.NewInMemoryStore()
	familyID := uuid.New()

	// Create: root married to spouse, root has child, child has grandchild married to grandchild_spouse
	// With depth=1 from root, grandchild_spouse should be truncated
	// and the marriage between grandchild and grandchild_spouse should be removed

	root := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Root"}
	spouse := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Spouse"}
	child := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Child"}
	grandchild := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Grandchild"}
	grandchildSpouse := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Grandchild Spouse"}

	store.CreatePerson(root)
	store.CreatePerson(spouse)
	store.CreatePerson(child)
	store.CreatePerson(grandchild)
	store.CreatePerson(grandchildSpouse)

	// Root and spouse are married
	marriage1 := &projection.Marriage{
		ID:         uuid.New(),
		FamilyID:   familyID,
		Partner1ID: root.ID,
		Partner2ID: spouse.ID,
		Status:     projection.MarriageActive,
	}
	store.CreateMarriage(marriage1)

	// Grandchild and grandchild_spouse are married
	marriage2 := &projection.Marriage{
		ID:         uuid.New(),
		FamilyID:   familyID,
		Partner1ID: grandchild.ID,
		Partner2ID: grandchildSpouse.ID,
		Status:     projection.MarriageActive,
	}
	store.CreateMarriage(marriage2)

	// root is parent of child
	rel1 := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: root.ID,
		ChildID:  child.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}
	// child is parent of grandchild
	rel2 := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: child.ID,
		ChildID:  grandchild.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}

	store.CreateRelationship(rel1)
	store.CreateRelationship(rel2)

	service := NewGraphService(store)

	graph, err := service.GetGraph(GetGraphRequest{
		FamilyID:     familyID,
		RootPersonID: root.ID,
		Depth:        1,
	})

	if err != nil {
		t.Fatalf("GetGraph failed: %v", err)
	}

	if !graph.Truncated {
		t.Error("expected truncated=true, got false")
	}

	// Build a set of person IDs in the response
	personIDs := make(map[string]bool)
	for _, p := range graph.Persons {
		personIDs[p.ID] = true
	}

	// Verify no marriage references a missing person
	for _, m := range graph.Marriages {
		if !personIDs[m.PartnerIDs[0]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[0])
		}
		if !personIDs[m.PartnerIDs[1]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[1])
		}
	}

	// Verify no relationship references a missing person
	for _, r := range graph.Relationships {
		if !personIDs[r.ParentID] {
			t.Errorf("relationship %s references missing parent %s", r.ID, r.ParentID)
		}
		if !personIDs[r.ChildID] {
			t.Errorf("relationship %s references missing child %s", r.ID, r.ChildID)
		}
	}

	// The grandchild_spouse should NOT be in persons (truncated) and marriage2 should be filtered out
	if personIDs[grandchildSpouse.ID.String()] {
		t.Error("grandchild_spouse should have been truncated")
	}

	// marriage1 (root-spouse) should be present, marriage2 should be filtered
	if len(graph.Marriages) != 1 {
		t.Errorf("expected 1 marriage, got %d", len(graph.Marriages))
	}
}

func TestGetGraph_NoTruncation_AllEdgesPresent(t *testing.T) {
	store := projection.NewInMemoryStore()
	familyID := uuid.New()

	// Create a simple family tree that fits within depth
	root := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Root"}
	child := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Child"}

	store.CreatePerson(root)
	store.CreatePerson(child)

	rel := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: root.ID,
		ChildID:  child.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}
	store.CreateRelationship(rel)

	service := NewGraphService(store)

	graph, err := service.GetGraph(GetGraphRequest{
		FamilyID:     familyID,
		RootPersonID: root.ID,
		Depth:        3,
	})

	if err != nil {
		t.Fatalf("GetGraph failed: %v", err)
	}

	if graph.Truncated {
		t.Error("expected truncated=false, got true")
	}

	if len(graph.Persons) != 2 {
		t.Errorf("expected 2 persons, got %d", len(graph.Persons))
	}

	if len(graph.Relationships) != 1 {
		t.Errorf("expected 1 relationship, got %d", len(graph.Relationships))
	}
}

func TestGetGraph_DepthExceedsMax_Returns400NotTruncated(t *testing.T) {
	// This test documents that depth > MaxDepth is handled by the HTTP layer (400 error)
	// not by truncation. The service itself accepts any depth.
	store := projection.NewInMemoryStore()
	familyID := uuid.New()

	root := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Root"}
	store.CreatePerson(root)

	service := NewGraphService(store)

	// Service accepts any depth - it's the HTTP handler that validates MaxDepth
	graph, err := service.GetGraph(GetGraphRequest{
		FamilyID:     familyID,
		RootPersonID: root.ID,
		Depth:        10, // Exceeds MaxDepth=8, but service doesn't enforce this
	})

	if err != nil {
		t.Fatalf("GetGraph should not fail for large depth: %v", err)
	}

	// No truncation because there's nothing to truncate
	if graph.Truncated {
		t.Error("expected truncated=false for single person")
	}
}
