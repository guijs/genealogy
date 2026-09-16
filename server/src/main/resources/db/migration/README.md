# Flyway 迁移脚本

本目录包含由 Flyway 管理的 PostgreSQL 数据库迁移脚本。

## 命名规范

迁移文件必须遵循 Flyway 的命名规范：

```
V{version}__{description}.sql
```

示例（本目录中的实际文件）：
- `V1__initial_schema.sql`
- `V2__users.sql`
- `V3__progenitor.sql`

## 当前迁移

| 版本 | 描述 |
|------|------|
| V1 | 初始 schema（families、family_members、persons、relationships、unions） |
| V2 | 用于 JWT 认证的 users 表 |
| V3 | 始祖（progenitor）支持 |
| V4 | 世代名称（generation names） |
| V5 | 故事（stories）功能 |

迁移会在 Spring Boot 启动时通过 `flyway.baseline-on-migrate: true` 自动执行。
