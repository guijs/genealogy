package kinship

import (
	"testing"

	"github.com/google/uuid"
)

func newPersonID() PersonID {
	return uuid.New()
}

func TestGraph_SelfLoop_Rejected(t *testing.T) {
	g := NewGraph()
	person := newPersonID()

	err := g.AddRelation(Relation{
		From: person,
		To:   person,
		Type: RelationBiologicalFather,
	})

	if err != ErrSelfLoop {
		t.Errorf("expected ErrSelfLoop, got %v", err)
	}
}

func TestGraph_MutualParent_Rejected(t *testing.T) {
	g := NewGraph()
	personA := newPersonID()
	personB := newPersonID()

	err := g.AddRelation(Relation{
		From: personA,
		To:   personB,
		Type: RelationBiologicalFather,
	})
	if err != nil {
		t.Fatalf("first relation should succeed: %v", err)
	}

	err = g.AddRelation(Relation{
		From: personB,
		To:   personA,
		Type: RelationBiologicalFather,
	})
	if err != ErrCycleDetected {
		t.Errorf("expected ErrCycleDetected for mutual parent, got %v", err)
	}
}

func TestGraph_ThreeHopCycle_Rejected(t *testing.T) {
	g := NewGraph()
	personA := newPersonID()
	personB := newPersonID()
	personC := newPersonID()

	if err := g.AddRelation(Relation{
		From: personA,
		To:   personB,
		Type: RelationBiologicalFather,
	}); err != nil {
		t.Fatalf("A->B should succeed: %v", err)
	}

	if err := g.AddRelation(Relation{
		From: personB,
		To:   personC,
		Type: RelationBiologicalFather,
	}); err != nil {
		t.Fatalf("B->C should succeed: %v", err)
	}

	err := g.AddRelation(Relation{
		From: personC,
		To:   personA,
		Type: RelationBiologicalFather,
	})
	if err != ErrCycleDetected {
		t.Errorf("expected ErrCycleDetected for 3-hop cycle (C->A), got %v", err)
	}
}

func TestGraph_DualBiologicalFather_Rejected(t *testing.T) {
	g := NewGraph()
	child := newPersonID()
	father1 := newPersonID()
	father2 := newPersonID()

	err := g.AddRelation(Relation{
		From: father1,
		To:   child,
		Type: RelationBiologicalFather,
	})
	if err != nil {
		t.Fatalf("first biological father should succeed: %v", err)
	}

	err = g.AddRelation(Relation{
		From: father2,
		To:   child,
		Type: RelationBiologicalFather,
	})
	if err != ErrDualBiologicalFather {
		t.Errorf("expected ErrDualBiologicalFather, got %v", err)
	}
}

func TestGraph_DualBiologicalMother_Rejected(t *testing.T) {
	g := NewGraph()
	child := newPersonID()
	mother1 := newPersonID()
	mother2 := newPersonID()

	err := g.AddRelation(Relation{
		From: mother1,
		To:   child,
		Type: RelationBiologicalMother,
	})
	if err != nil {
		t.Fatalf("first biological mother should succeed: %v", err)
	}

	err = g.AddRelation(Relation{
		From: mother2,
		To:   child,
		Type: RelationBiologicalMother,
	})
	if err != ErrDualBiologicalMother {
		t.Errorf("expected ErrDualBiologicalMother, got %v", err)
	}
}

func TestGraph_DualAdoptiveFather_Rejected(t *testing.T) {
	g := NewGraph()
	child := newPersonID()
	adoptiveFather1 := newPersonID()
	adoptiveFather2 := newPersonID()

	err := g.AddRelation(Relation{
		From: adoptiveFather1,
		To:   child,
		Type: RelationAdoptiveFather,
	})
	if err != nil {
		t.Fatalf("first adoptive father should succeed: %v", err)
	}

	err = g.AddRelation(Relation{
		From: adoptiveFather2,
		To:   child,
		Type: RelationAdoptiveFather,
	})
	if err != ErrDualAdoptiveFather {
		t.Errorf("expected ErrDualAdoptiveFather, got %v", err)
	}
}

func TestGraph_DualAdoptiveMother_Rejected(t *testing.T) {
	g := NewGraph()
	child := newPersonID()
	adoptiveMother1 := newPersonID()
	adoptiveMother2 := newPersonID()

	err := g.AddRelation(Relation{
		From: adoptiveMother1,
		To:   child,
		Type: RelationAdoptiveMother,
	})
	if err != nil {
		t.Fatalf("first adoptive mother should succeed: %v", err)
	}

	err = g.AddRelation(Relation{
		From: adoptiveMother2,
		To:   child,
		Type: RelationAdoptiveMother,
	})
	if err != ErrDualAdoptiveMother {
		t.Errorf("expected ErrDualAdoptiveMother, got %v", err)
	}
}

func TestGraph_BiologicalAndAdoptiveFather_Coexist(t *testing.T) {
	g := NewGraph()
	child := newPersonID()
	bioFather := newPersonID()
	adoptiveFather := newPersonID()

	err := g.AddRelation(Relation{
		From: bioFather,
		To:   child,
		Type: RelationBiologicalFather,
	})
	if err != nil {
		t.Fatalf("biological father should succeed: %v", err)
	}

	err = g.AddRelation(Relation{
		From: adoptiveFather,
		To:   child,
		Type: RelationAdoptiveFather,
	})
	if err != nil {
		t.Errorf("adoptive father should coexist with biological father, got %v", err)
	}

	parents := g.GetParents(child)
	if len(parents) != 2 {
		t.Errorf("child should have 2 parents (bio + adoptive), got %d", len(parents))
	}
}

func TestGraph_BiologicalAndAdoptiveMother_Coexist(t *testing.T) {
	g := NewGraph()
	child := newPersonID()
	bioMother := newPersonID()
	adoptiveMother := newPersonID()

	err := g.AddRelation(Relation{
		From: bioMother,
		To:   child,
		Type: RelationBiologicalMother,
	})
	if err != nil {
		t.Fatalf("biological mother should succeed: %v", err)
	}

	err = g.AddRelation(Relation{
		From: adoptiveMother,
		To:   child,
		Type: RelationAdoptiveMother,
	})
	if err != nil {
		t.Errorf("adoptive mother should coexist with biological mother, got %v", err)
	}

	parents := g.GetParents(child)
	if len(parents) != 2 {
		t.Errorf("child should have 2 parents (bio + adoptive), got %d", len(parents))
	}
}

func TestGraph_ValidFamilyTree(t *testing.T) {
	g := NewGraph()

	grandpa := newPersonID()
	grandma := newPersonID()
	father := newPersonID()
	mother := newPersonID()
	child := newPersonID()

	if err := g.AddRelation(Relation{
		From: grandpa,
		To:   father,
		Type: RelationBiologicalFather,
	}); err != nil {
		t.Fatalf("grandpa->father should succeed: %v", err)
	}

	if err := g.AddRelation(Relation{
		From: grandma,
		To:   father,
		Type: RelationBiologicalMother,
	}); err != nil {
		t.Fatalf("grandma->father should succeed: %v", err)
	}

	if err := g.AddRelation(Relation{
		From: father,
		To:   child,
		Type: RelationBiologicalFather,
	}); err != nil {
		t.Fatalf("father->child should succeed: %v", err)
	}

	if err := g.AddRelation(Relation{
		From: mother,
		To:   child,
		Type: RelationBiologicalMother,
	}); err != nil {
		t.Fatalf("mother->child should succeed: %v", err)
	}

	parents := g.GetParents(child)
	if len(parents) != 2 {
		t.Errorf("child should have 2 parents, got %d", len(parents))
	}

	fatherParents := g.GetParents(father)
	if len(fatherParents) != 2 {
		t.Errorf("father should have 2 parents, got %d", len(fatherParents))
	}
}
