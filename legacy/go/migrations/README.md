# 数据库迁移

本目录将包含由 golang-migrate 管理的 PostgreSQL 迁移脚本。

## 计划中的 schema（尚未实现）

- `families` - 家族树容器
- `persons` - 家族树中的个人
- `kinship_edges` - 亲子关系（无 person.parent_id 列）
- `unions` - 婚姻/伴侣关系

迁移将在后续切片中添加（B1：Persons、B3：Relationships）。
