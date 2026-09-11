package projection

import (
	"encoding/json"
	"strings"
	"testing"
)

func intPtr(i int) *int    { return &i }
func strPtr(s string) *string { return &s }

func TestGraphProjection_JSONFormat(t *testing.T) {
	reason := "depth limit reached"
	graph := GraphProjection{
		FamilyID:       "family-123",
		RootPersonID:   "person-456",
		Depth:          3,
		Truncated:      true,
		TruncateReason: &reason,
		Persons: []PersonDTO{
			{
				ID:          "p1",
				DisplayName: "John Doe",
				Gender:      GenderMale,
				BirthYear:   intPtr(1980),
				Deceased:    false,
			},
		},
		Marriages: []MarriageDTO{
			{
				ID:          "m1",
				PartnerIDs:  [2]string{"p1", "p2"},
				Status:      MarriageDivorced,
				EndedReason: strPtr("irreconcilable differences"),
			},
		},
		Relationships: []RelationshipDTO{
			{
				ID:       "r1",
				Type:     "PARENT_CHILD",
				Subtype:  SubtypeBiological,
				ParentID: "p1",
				ChildID:  "p3",
				Role:     RoleFather,
			},
		},
	}

	out, err := json.MarshalIndent(graph, "", "  ")
	if err != nil {
		t.Fatalf("failed to marshal: %v", err)
	}

	jsonStr := string(out)

	requiredFields := []string{
		`"familyId"`,
		`"rootPersonId"`,
		`"depth"`,
		`"truncated"`,
		`"truncateReason"`,
		`"persons"`,
		`"marriages"`,
		`"relationships"`,
		`"displayName"`,
		`"partnerIds"`,
		`"parentId"`,
		`"childId"`,
		`"subtype"`,
	}

	for _, field := range requiredFields {
		if !strings.Contains(jsonStr, field) {
			t.Errorf("missing field %s in JSON output", field)
		}
	}

	forbiddenFields := []string{
		`"parent_id"`,
		`"child_id"`,
		`"partner_ids"`,
		`"family_id"`,
		`"root_person_id"`,
		`"display_name"`,
		`"birth_year"`,
		`"death_year"`,
		`"Hidden"`,
		`"Dissolved"`,
	}

	for _, field := range forbiddenFields {
		if strings.Contains(jsonStr, field) {
			t.Errorf("found forbidden field %s in JSON output (should use camelCase)", field)
		}
	}

	t.Logf("JSON output:\n%s", jsonStr)
}

func TestMarriageDTO_IncludesEndedStatus(t *testing.T) {
	statuses := []MarriageStatus{
		MarriageActive,
		MarriageDivorced,
		MarriageWidowed,
		MarriageEnded,
	}

	for _, status := range statuses {
		m := MarriageDTO{
			ID:         "m1",
			PartnerIDs: [2]string{"p1", "p2"},
			Status:     status,
		}
		out, _ := json.Marshal(m)
		if !strings.Contains(string(out), string(status)) {
			t.Errorf("status %s not found in JSON", status)
		}
	}
}

func TestRelationshipDTO_TypeIsPARENT_CHILD(t *testing.T) {
	r := RelationshipDTO{
		ID:       "r1",
		Type:     "PARENT_CHILD",
		Subtype:  SubtypeBiological,
		ParentID: "p1",
		ChildID:  "p2",
	}

	out, _ := json.Marshal(r)
	if !strings.Contains(string(out), `"type":"PARENT_CHILD"`) {
		t.Errorf("type should be PARENT_CHILD, got: %s", string(out))
	}
}
