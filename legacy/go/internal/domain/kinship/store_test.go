package kinship

import (
	"testing"

	"github.com/google/uuid"
)

func TestStore_AddRelation_CycleRejected(t *testing.T) {
	store := NewInMemoryStore()
	familyID := uuid.New()
	personA := uuid.New()
	personB := uuid.New()

	rel1 := Relation{From: personA, To: personB, Type: RelationBiologicalFather}
	if err := store.AddRelation(familyID, rel1); err != nil {
		t.Fatalf("first relation should succeed: %v", err)
	}

	rel2 := Relation{From: personB, To: personA, Type: RelationBiologicalFather}
	err := store.AddRelation(familyID, rel2)
	if err != ErrCycleDetected {
		t.Errorf("store.AddRelation should reject cycle, got: %v", err)
	}

	graph := store.GetGraphForFamily(familyID)
	if len(graph.Relations()) != 1 {
		t.Errorf("cycle relation should not be saved, got %d relations", len(graph.Relations()))
	}
}

func TestStore_AddRelation_DualBioFatherRejected(t *testing.T) {
	store := NewInMemoryStore()
	familyID := uuid.New()
	child := uuid.New()
	father1 := uuid.New()
	father2 := uuid.New()

	rel1 := Relation{From: father1, To: child, Type: RelationBiologicalFather}
	if err := store.AddRelation(familyID, rel1); err != nil {
		t.Fatalf("first bio father should succeed: %v", err)
	}

	rel2 := Relation{From: father2, To: child, Type: RelationBiologicalFather}
	err := store.AddRelation(familyID, rel2)
	if err != ErrDualBiologicalFather {
		t.Errorf("store.AddRelation should reject dual bio father, got: %v", err)
	}

	graph := store.GetGraphForFamily(familyID)
	if len(graph.Relations()) != 1 {
		t.Errorf("invalid relation should not be saved, got %d relations", len(graph.Relations()))
	}
}

func TestStore_AddRelation_DualAdoptiveFatherRejected(t *testing.T) {
	store := NewInMemoryStore()
	familyID := uuid.New()
	child := uuid.New()
	father1 := uuid.New()
	father2 := uuid.New()

	rel1 := Relation{From: father1, To: child, Type: RelationAdoptiveFather}
	if err := store.AddRelation(familyID, rel1); err != nil {
		t.Fatalf("first adoptive father should succeed: %v", err)
	}

	rel2 := Relation{From: father2, To: child, Type: RelationAdoptiveFather}
	err := store.AddRelation(familyID, rel2)
	if err != ErrDualAdoptiveFather {
		t.Errorf("store.AddRelation should reject dual adoptive father, got: %v", err)
	}

	graph := store.GetGraphForFamily(familyID)
	if len(graph.Relations()) != 1 {
		t.Errorf("invalid relation should not be saved, got %d relations", len(graph.Relations()))
	}
}

func TestStore_AddRelation_SelfLoopRejected(t *testing.T) {
	store := NewInMemoryStore()
	familyID := uuid.New()
	person := uuid.New()

	rel := Relation{From: person, To: person, Type: RelationBiologicalFather}
	err := store.AddRelation(familyID, rel)
	if err != ErrSelfLoop {
		t.Errorf("store.AddRelation should reject self-loop, got: %v", err)
	}

	graph := store.GetGraphForFamily(familyID)
	if len(graph.Relations()) != 0 {
		t.Errorf("self-loop should not be saved, got %d relations", len(graph.Relations()))
	}
}

func TestStore_AddRelation_ValidRelationSaved(t *testing.T) {
	store := NewInMemoryStore()
	familyID := uuid.New()
	parent := uuid.New()
	child := uuid.New()

	rel := Relation{From: parent, To: child, Type: RelationBiologicalFather}
	if err := store.AddRelation(familyID, rel); err != nil {
		t.Fatalf("valid relation should succeed: %v", err)
	}

	graph := store.GetGraphForFamily(familyID)
	if len(graph.Relations()) != 1 {
		t.Errorf("valid relation should be saved, got %d relations", len(graph.Relations()))
	}
}
