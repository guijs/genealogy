package kinship

import "errors"

var (
	ErrSelfLoop             = errors.New("kinship: person cannot be their own parent")
	ErrCycleDetected        = errors.New("kinship: cycle detected in family graph")
	ErrDualBiologicalFather = errors.New("kinship: person already has a biological father")
	ErrDualBiologicalMother = errors.New("kinship: person already has a biological mother")
	ErrDualAdoptiveFather   = errors.New("kinship: person already has an adoptive father")
	ErrDualAdoptiveMother   = errors.New("kinship: person already has an adoptive mother")
	ErrMutualParent         = errors.New("kinship: mutual parent relationship not allowed")
)
