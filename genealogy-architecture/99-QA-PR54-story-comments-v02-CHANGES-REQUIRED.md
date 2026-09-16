# QA Report: PR#54 Story Comments v0.2 - CHANGES REQUIRED

> QA: ⑥  
> PR: #54  
> Original HEAD: `0ccfd7f`  
> Status: **CHANGES REQUIRED** → **FIXED**

---

## MAJOR-1: Viewer can edit/delete own comments

### Issue
`updateComment` / `deleteComment` did not require write role. `WriteAccessFilter` only gated POST on comments, not PUT|DELETE. A user who authored a comment then gets demoted to **viewer** could still edit/delete own comments — violates v0.2 「viewer 只读」.

### Root Cause
`WriteAccessFilter.isWriteEndpoint()` only checked:
```java
if ("POST".equals(method) && COMMENTS_POST_PATH_PATTERN.matcher(path).matches()) {
    return true;
}
```

Missing check for PUT|DELETE on individual comments (`/comments/{id}`).

### Fix Applied

1. **WriteAccessFilter**: Added `COMMENTS_ITEM_PATH_PATTERN` and check for PUT|DELETE:
```java
private static final Pattern COMMENTS_ITEM_PATH_PATTERN = 
    Pattern.compile("^/api/v1/families/[^/]+/stories/[^/]+/comments/[^/]+$");

// In isWriteEndpoint():
if (("PUT".equals(method) || "DELETE".equals(method)) && COMMENTS_ITEM_PATH_PATTERN.matcher(path).matches()) {
    return true;
}
```

2. **Service layer checks retained**:
   - Author-only edit: `NotAuthorException` in `updateComment`
   - Admin-only delete others: `Role.ADMIN` check in `deleteComment`

3. **Regression tests added**:
   - `regression_viewerCannotEditOwnComment_returns403()`
   - `regression_viewerCannotDeleteOwnComment_returns403()`

### Verification

| Scenario | Expected | Result |
|----------|----------|--------|
| Editor creates comment | 201 | ✅ |
| Editor demoted to viewer | — | — |
| Viewer PUT own comment | 403 "write access required" | ✅ |
| Viewer DELETE own comment | 403 "write access required" | ✅ |
| Admin PUT other's comment | 403 "only author can edit" | ✅ |
| Editor DELETE other's comment | 403 "only author or admin can delete" | ✅ |
| Admin DELETE other's comment | 204 | ✅ |

---

## Test Summary

- **Total tests**: 402 (was 400, +2 regression tests)
- **Passing**: 402
- **Failing**: 0

---

## Files Changed

1. `WriteAccessFilter.java` - Added PUT|DELETE gate for comments
2. `StoryCommentControllerTest.java` - Added 2 regression tests

---

## Conclusion

MAJOR-1 fixed. All comment writes (POST/PUT/DELETE) now require `canWrite()` role (admin/editor). Viewer is fully read-only per v0.2 spec.
