# RBAC（基于角色的访问控制）角色矩阵（P0）

## 角色

| 角色 | 描述 | P0 状态 |
|------|------|---------|
| `admin` | 创建者/所有者，拥有完全访问权限 | ✅ 已实现 |
| `editor` | 写入权限，无成员管理权限 | 🔒 保留（无 API） |
| `viewer` | 只读访问权限 | 🔒 保留（无 API） |

## P0 约束

- **无所有权转移端点** — P0 阶段 admin 角色永久不变
- **无角色变更端点** — P0 阶段无法修改角色
- **无邀请 API** — 成员仅通过测试 fixture 或未来 P1 工作添加

## 权限矩阵

| 操作 | admin | editor | viewer | 非成员 |
|------|-------|--------|--------|--------|
| GET /families/{id} | ✅ 200 | ✅ 200 | ✅ 200 | ❌ 404 |
| GET /families/{id}/persons | ✅ 200 | ✅ 200 | ✅ 200 | ❌ 404 |
| POST /families/{id}/relationships | ✅ 201 | ✅ 201 | ❌ 403 | ❌ 404 |
| POST /families/{id}/media/upload-url | ✅ 200 | ✅ 200 | ❌ 403 | ❌ 404 |

## 安全说明

1. **非成员返回 404（而非 403）** — 防止 IDOR（不安全直接对象引用）枚举
2. **查看者写操作返回 403** — 明确的权限拒绝（他们知道家族存在）
3. **优先检查认证** — 未认证请求返回 401

## 中间件链

```
RequireAuth → RequireFamilyMember → [RequireWriteAccess（写端点）]
```

- `RequireAuth`：验证 X-User-Id 头（开发存根，后续接入真实 IdP）
- `RequireFamilyMember`：检查成员身份，将角色存入上下文
- `RequireWriteAccess`：检查 role.CanWrite()，如为 false 则返回 403

## 测试覆盖

P0 测试必须覆盖以下矩阵行：
- [x] admin 可读取 → 200
- [x] admin 可写入 → 201
- [x] viewer 可读取 → 200
- [x] viewer 不能写入 → 403
- [x] 非成员不能读取 → 404
- [x] 非成员不能写入 → 404
- [x] 未认证 → 401
