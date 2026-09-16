# QA 报告：PR#54 故事评论 v0.2 - 需要修改

> QA：⑥  
> PR：#54  
> 原始 HEAD：`0ccfd7f`  
> 状态：**需要修改** → **已修复**

---

## MAJOR-1：查看者可以编辑/删除自己的评论

### 问题
`updateComment` / `deleteComment` 没有要求写入角色。`WriteAccessFilter` 仅对 POST 评论请求进行拦截，未处理 PUT|DELETE。一个创作了评论的用户被降级为 **viewer（查看者）** 后仍能编辑/删除自己的评论 — 违反 v0.2「viewer 只读」规范。

### 根本原因
`WriteAccessFilter.isWriteEndpoint()` 仅检查：
```java
if ("POST".equals(method) && COMMENTS_POST_PATH_PATTERN.matcher(path).matches()) {
    return true;
}
```

缺少对单条评论（`/comments/{id}`）PUT|DELETE 的检查。

### 已应用修复

1. **WriteAccessFilter**：添加 `COMMENTS_ITEM_PATH_PATTERN` 及 PUT|DELETE 检查：
```java
private static final Pattern COMMENTS_ITEM_PATH_PATTERN = 
    Pattern.compile("^/api/v1/families/[^/]+/stories/[^/]+/comments/[^/]+$");

// 在 isWriteEndpoint() 中：
if (("PUT".equals(method) || "DELETE".equals(method)) && COMMENTS_ITEM_PATH_PATTERN.matcher(path).matches()) {
    return true;
}
```

2. **服务层检查保留**：
   - 仅作者可编辑：`updateComment` 中的 `NotAuthorException`
   - 仅管理员可删除他人评论：`deleteComment` 中的 `Role.ADMIN` 检查

3. **新增回归测试**：
   - `regression_viewerCannotEditOwnComment_returns403()`
   - `regression_viewerCannotDeleteOwnComment_returns403()`

### 验证结果

| 场景 | 预期 | 结果 |
|------|------|------|
| 编辑者创建评论 | 201 | ✅ |
| 编辑者被降级为查看者 | — | — |
| 查看者 PUT 自己的评论 | 403 "需要写入权限" | ✅ |
| 查看者 DELETE 自己的评论 | 403 "需要写入权限" | ✅ |
| 管理员 PUT 他人评论 | 403 "仅作者可编辑" | ✅ |
| 编辑者 DELETE 他人评论 | 403 "仅作者或管理员可删除" | ✅ |
| 管理员 DELETE 他人评论 | 204 | ✅ |

---

## 测试摘要

- **总测试数**：402（原为 400，+2 回归测试）
- **通过**：402
- **失败**：0

---

## 变更文件

1. `WriteAccessFilter.java` - 添加评论 PUT|DELETE 拦截
2. `StoryCommentControllerTest.java` - 添加 2 个回归测试

---

## 结论

MAJOR-1 已修复。所有评论写操作（POST/PUT/DELETE）现在都需要 `canWrite()` 角色（admin/editor）。Viewer（查看者）完全只读，符合 v0.2 规范。
