# RBAC Role Matrix (P0)

## Roles

| Role | Description | P0 Status |
|------|-------------|-----------|
| `admin` | Creator/owner with full access | ✅ Implemented |
| `editor` | Write access, no member management | 🔒 Reserved (no APIs) |
| `viewer` | Read-only access | 🔒 Reserved (no APIs) |

## P0 Constraints

- **NO owner-transfer endpoints** - admin role is permanent in P0
- **NO change-role endpoints** - roles cannot be modified in P0
- **NO invite APIs** - members added only via test fixtures or future P1 work

## Permission Matrix

| Action | admin | editor | viewer | non-member |
|--------|-------|--------|--------|------------|
| GET /families/{id} | ✅ 200 | ✅ 200 | ✅ 200 | ❌ 404 |
| GET /families/{id}/persons | ✅ 200 | ✅ 200 | ✅ 200 | ❌ 404 |
| POST /families/{id}/relationships | ✅ 201 | ✅ 201 | ❌ 403 | ❌ 404 |
| POST /families/{id}/media/upload-url | ✅ 200 | ✅ 200 | ❌ 403 | ❌ 404 |

## Security Notes

1. **Non-member returns 404 (not 403)** - Prevents IDOR enumeration
2. **Viewer gets 403 for writes** - Clear permission denied (they know family exists)
3. **Authentication checked first** - Unauthenticated requests get 401

## Middleware Chain

```
RequireAuth → RequireFamilyMember → [RequireWriteAccess for write endpoints]
```

- `RequireAuth`: Validates X-User-Id header (dev stub, real IdP later)
- `RequireFamilyMember`: Checks membership, stores role in context
- `RequireWriteAccess`: Checks role.CanWrite(), returns 403 if false

## Test Coverage

Tests must cover these matrix rows for P0:
- [x] admin can read → 200
- [x] admin can write → 201
- [x] viewer can read → 200
- [x] viewer cannot write → 403
- [x] non-member cannot read → 404
- [x] non-member cannot write → 404
- [x] unauthenticated → 401
