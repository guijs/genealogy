package kinship

import "github.com/google/uuid"

type PersonID = uuid.UUID

type RelationType string

const (
	RelationBiologicalFather RelationType = "biological_father"
	RelationBiologicalMother RelationType = "biological_mother"
	RelationAdoptiveFather   RelationType = "adoptive_father"
	RelationAdoptiveMother   RelationType = "adoptive_mother"
)

func (r RelationType) IsBiologicalParent() bool {
	return r == RelationBiologicalFather || r == RelationBiologicalMother
}

func (r RelationType) IsParentRole() bool {
	switch r {
	case RelationBiologicalFather, RelationBiologicalMother,
		RelationAdoptiveFather, RelationAdoptiveMother:
		return true
	}
	return false
}

type Relation struct {
	From PersonID
	To   PersonID
	Type RelationType
}

type Graph struct {
	relations []Relation
	children  map[PersonID][]Relation
}

func NewGraph() *Graph {
	return &Graph{
		relations: make([]Relation, 0),
		children:  make(map[PersonID][]Relation),
	}
}

func (g *Graph) AddRelation(rel Relation) error {
	if rel.From == rel.To {
		return ErrSelfLoop
	}

	if err := g.validateParentUniqueness(rel); err != nil {
		return err
	}

	if g.wouldCreateCycle(rel) {
		return ErrCycleDetected
	}

	g.relations = append(g.relations, rel)
	g.children[rel.To] = append(g.children[rel.To], rel)
	return nil
}

func (g *Graph) validateParentUniqueness(newRel Relation) error {
	if !newRel.Type.IsParentRole() {
		return nil
	}

	for _, existing := range g.children[newRel.To] {
		if existing.Type == newRel.Type && existing.From != newRel.From {
			switch newRel.Type {
			case RelationBiologicalFather:
				return ErrDualBiologicalFather
			case RelationBiologicalMother:
				return ErrDualBiologicalMother
			case RelationAdoptiveFather:
				return ErrDualAdoptiveFather
			case RelationAdoptiveMother:
				return ErrDualAdoptiveMother
			}
		}
	}
	return nil
}

func (g *Graph) wouldCreateCycle(newRel Relation) bool {
	visited := make(map[PersonID]bool)
	return g.isAncestor(newRel.From, newRel.To, visited)
}

func (g *Graph) isAncestor(current, target PersonID, visited map[PersonID]bool) bool {
	if current == target {
		return true
	}
	if visited[current] {
		return false
	}
	visited[current] = true

	for _, rel := range g.children[current] {
		if g.isAncestor(rel.From, target, visited) {
			return true
		}
	}
	return false
}

func (g *Graph) GetParents(personID PersonID) []Relation {
	return g.children[personID]
}

func (g *Graph) Relations() []Relation {
	result := make([]Relation, len(g.relations))
	copy(result, g.relations)
	return result
}
