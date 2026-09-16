# 旧版 Go 后端（已归档）

**状态：** ⚠️ 已归档 — 仅供参考的只读代码

本目录包含原始的 Go 后端实现。自第 0 阶段迁移至 Java/Spring Boot 后已归档。

## 活跃后端

当前活跃的后端位于 `server/`（Spring Boot + JDK 17 + MyBatis）。

**请勿修改或扩展此代码。** 仅作为历史参考保留。

## 原始结构

```
legacy/go/
├── cmd/api/              # 原 Go API 入口点
├── internal/
│   ├── domain/           # 业务逻辑（person、kinship、family）
│   ├── app/              # 应用服务
│   ├── adapter/          # 适配器（HTTP、Postgres、ObjectStore）
│   └── platform/         # 基础设施
├── migrations/           # 原 golang-migrate 迁移脚本
├── go.mod
└── go.sum
```

## 迁移说明

- Go 代码库实现了 IDOR 防护、亲属图谱和关系服务
- 领域逻辑和 API 契约将在第 1 阶段及之后移植到 Java
- 此代码作为新实现的规范参考
