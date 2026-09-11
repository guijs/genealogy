package family

type Role string

const (
	// RoleAdmin is the creator/owner role with full write access.
	// In P0, this is the only role that can write.
	RoleAdmin Role = "admin"

	// RoleEditor is reserved for future use.
	// Will have write access but cannot manage members.
	// NOT IMPLEMENTED IN P0 - no invite/transfer APIs.
	RoleEditor Role = "editor"

	// RoleViewer is reserved for future use.
	// Read-only access to family data.
	// NOT IMPLEMENTED IN P0 - no invite/transfer APIs.
	RoleViewer Role = "viewer"
)

func (r Role) IsValid() bool {
	switch r {
	case RoleAdmin, RoleEditor, RoleViewer:
		return true
	}
	return false
}

func (r Role) CanWrite() bool {
	switch r {
	case RoleAdmin, RoleEditor:
		return true
	}
	return false
}

func (r Role) CanRead() bool {
	return r.IsValid()
}
