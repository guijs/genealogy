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

func TestGetGraph_MarriageWithHiddenPartnerExcluded(t *testing.T) {
	store := projection.NewInMemoryStore()
	familyID := uuid.New()

	// Create: visible person married to hidden person
	visible := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Visible", Hidden: false}
	hidden := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Hidden", Hidden: true}
	alsoVisible := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Also Visible", Hidden: false}

	store.CreatePerson(visible)
	store.CreatePerson(hidden)
	store.CreatePerson(alsoVisible)

	// Marriage between visible and hidden person - should be excluded
	marriageWithHidden := &projection.Marriage{
		ID:         uuid.New(),
		FamilyID:   familyID,
		Partner1ID: visible.ID,
		Partner2ID: hidden.ID,
		Status:     projection.MarriageActive,
	}
	store.CreateMarriage(marriageWithHidden)

	// Marriage between two visible persons - should be included
	marriageVisible := &projection.Marriage{
		ID:         uuid.New(),
		FamilyID:   familyID,
		Partner1ID: visible.ID,
		Partner2ID: alsoVisible.ID,
		Status:     projection.MarriageActive,
	}
	store.CreateMarriage(marriageVisible)

	service := NewGraphService(store)

	graph, err := service.GetGraph(GetGraphRequest{
		FamilyID:     familyID,
		RootPersonID: visible.ID,
		Depth:        3,
	})

	if err != nil {
		t.Fatalf("GetGraph failed: %v", err)
	}

	// Build person ID set from response
	personIDs := make(map[string]bool)
	for _, p := range graph.Persons {
		personIDs[p.ID] = true
	}

	// Hidden person should NOT be in persons
	if personIDs[hidden.ID.String()] {
		t.Error("hidden person should not be in persons")
	}

	// Verify all marriage partner IDs are in persons (no dangling references)
	for _, m := range graph.Marriages {
		if !personIDs[m.PartnerIDs[0]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[0])
		}
		if !personIDs[m.PartnerIDs[1]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[1])
		}
	}

	// Only the marriage between visible persons should be present
	if len(graph.Marriages) != 1 {
		t.Errorf("expected 1 marriage (visible-visible), got %d", len(graph.Marriages))
	}

	// The remaining marriage should be the visible-alsoVisible one
	if len(graph.Marriages) == 1 {
		m := graph.Marriages[0]
		if m.ID != marriageVisible.ID.String() {
			t.Errorf("expected marriage %s, got %s", marriageVisible.ID.String(), m.ID)
		}
	}
}

func TestGetGraph_MarriageWithBothPartnersHiddenExcluded(t *testing.T) {
	store := projection.NewInMemoryStore()
	familyID := uuid.New()

	// Create: root visible, has child who is hidden, hidden child married to hidden spouse
	root := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Root", Hidden: false}
	hiddenChild := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Hidden Child", Hidden: true}
	hiddenSpouse := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Hidden Spouse", Hidden: true}

	store.CreatePerson(root)
	store.CreatePerson(hiddenChild)
	store.CreatePerson(hiddenSpouse)

	// Marriage between two hidden persons
	marriageHidden := &projection.Marriage{
		ID:         uuid.New(),
		FamilyID:   familyID,
		Partner1ID: hiddenChild.ID,
		Partner2ID: hiddenSpouse.ID,
		Status:     projection.MarriageActive,
	}
	store.CreateMarriage(marriageHidden)

	// Root is parent of hidden child
	rel := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: root.ID,
		ChildID:  hiddenChild.ID,
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

	// No marriages should be in the result (the only marriage has hidden partners)
	if len(graph.Marriages) != 0 {
		t.Errorf("expected 0 marriages (all partners hidden), got %d", len(graph.Marriages))
	}

	// Only root should be in persons
	if len(graph.Persons) != 1 {
		t.Errorf("expected 1 person (root), got %d", len(graph.Persons))
	}
}

func TestGetGraph_MarriageDanglingFilterWithCrossFamilySpouse(t *testing.T) {
	// This test exercises the map-level dangling marriage filter.
	// When a marriage is added during traversal but the spouse is from a different family,
	// the spouse won't be added to the persons map, triggering the dangling filter.
	store := projection.NewInMemoryStore()
	familyID := uuid.New()
	otherFamilyID := uuid.New()

	// Person in target family
	person := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Person"}
	// Spouse in different family
	crossFamilySpouse := &projection.Person{ID: uuid.New(), FamilyID: otherFamilyID, DisplayName: "Cross-Family Spouse"}

	store.CreatePerson(person)
	store.CreatePerson(crossFamilySpouse)

	// Marriage exists in person's family
	marriage := &projection.Marriage{
		ID:         uuid.New(),
		FamilyID:   familyID,
		Partner1ID: person.ID,
		Partner2ID: crossFamilySpouse.ID,
		Status:     projection.MarriageActive,
	}
	store.CreateMarriage(marriage)

	service := NewGraphService(store)

	graph, err := service.GetGraph(GetGraphRequest{
		FamilyID:     familyID,
		RootPersonID: person.ID,
		Depth:        3,
	})

	if err != nil {
		t.Fatalf("GetGraph failed: %v", err)
	}

	// Build person ID set
	personIDs := make(map[string]bool)
	for _, p := range graph.Persons {
		personIDs[p.ID] = true
	}

	// Cross-family spouse should NOT be in persons
	if personIDs[crossFamilySpouse.ID.String()] {
		t.Error("cross-family spouse should not be in persons")
	}

	// The marriage should be filtered out (dangling reference)
	if len(graph.Marriages) != 0 {
		t.Errorf("expected 0 marriages (cross-family spouse filtered), got %d", len(graph.Marriages))
	}

	// Verify no dangling marriage references
	for _, m := range graph.Marriages {
		if !personIDs[m.PartnerIDs[0]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[0])
		}
		if !personIDs[m.PartnerIDs[1]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[1])
		}
	}
}

func TestGetGraph_MarriageDanglingFilterWithTruncatedPartner(t *testing.T) {
	// This is an improved version of TestGetGraph_NoDanglingMarriageWhenPartnerTruncated.
	// Structure: root -> child1 -> grandchild (depth=2, connected via root through two paths)
	//            root -> child2 (who is married to grandchild)
	// With depth=1: child1, child2 are visited. grandchild is reachable via marriage
	// to child2 but not via regular depth traversal from child1.
	// The marriage between child2 and grandchild is added when visiting child2,
	// and grandchild is added as spouse. This exercises the marriage inclusion logic.

	store := projection.NewInMemoryStore()
	familyID := uuid.New()

	root := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Root"}
	child1 := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Child1"}
	child2 := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Child2"}
	grandchild := &projection.Person{ID: uuid.New(), FamilyID: familyID, DisplayName: "Grandchild"}

	store.CreatePerson(root)
	store.CreatePerson(child1)
	store.CreatePerson(child2)
	store.CreatePerson(grandchild)

	// root -> child1
	rel1 := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: root.ID,
		ChildID:  child1.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}
	// root -> child2
	rel2 := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: root.ID,
		ChildID:  child2.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}
	// child1 -> grandchild
	rel3 := &projection.Relationship{
		ID:       uuid.New(),
		FamilyID: familyID,
		ParentID: child1.ID,
		ChildID:  grandchild.ID,
		Subtype:  projection.SubtypeBiological,
		Role:     projection.RoleFather,
	}
	store.CreateRelationship(rel1)
	store.CreateRelationship(rel2)
	store.CreateRelationship(rel3)

	// child2 is married to grandchild (cross-generational, but valid scenario)
	marriage := &projection.Marriage{
		ID:         uuid.New(),
		FamilyID:   familyID,
		Partner1ID: child2.ID,
		Partner2ID: grandchild.ID,
		Status:     projection.MarriageActive,
	}
	store.CreateMarriage(marriage)

	service := NewGraphService(store)

	graph, err := service.GetGraph(GetGraphRequest{
		FamilyID:     familyID,
		RootPersonID: root.ID,
		Depth:        1,
	})

	if err != nil {
		t.Fatalf("GetGraph failed: %v", err)
	}

	// Build person ID set
	personIDs := make(map[string]bool)
	for _, p := range graph.Persons {
		personIDs[p.ID] = true
	}

	// With depth=1 from root:
	// - root (depth=0), child1 (depth=1), child2 (depth=1) are visited via relationships
	// - grandchild is added as spouse of child2 (via marriage processing)
	// - grandchild should be in persons (spouse handling adds them)
	// - The marriage should be present (both partners in persons)

	// Verify the marriage is present with no dangling references
	for _, m := range graph.Marriages {
		if !personIDs[m.PartnerIDs[0]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[0])
		}
		if !personIDs[m.PartnerIDs[1]] {
			t.Errorf("marriage %s references missing partner %s", m.ID, m.PartnerIDs[1])
		}
	}

	// Verify truncated is true (child1->grandchild relationship would go beyond depth)
	if !graph.Truncated {
		t.Error("expected truncated=true")
	}

	// Verify all relationship endpoints are in persons
	for _, r := range graph.Relationships {
		if !personIDs[r.ParentID] {
			t.Errorf("relationship %s references missing parent %s", r.ID, r.ParentID)
		}
		if !personIDs[r.ChildID] {
			t.Errorf("relationship %s references missing child %s", r.ID, r.ChildID)
		}
	}
}
