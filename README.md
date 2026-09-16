# Genealogy（家谱系统）

现代家族谱系软件。

## 技术栈

- **后端：** Spring Boot 3.x (JDK 17) — 模块化单体架构，位于 `server/`
- **ORM：** MyBatis（不使用 JPA/Hibernate）
- **数据库迁移：** Flyway（不使用 Liquibase）
- **数据库：** PostgreSQL 16
- **前端：** Vue 3 + TypeScript + Vite，位于 `web/`

## 项目结构

```
.
├── server/               # 活跃的 Java 后端（Spring Boot）
│   ├── src/main/java/    # 应用代码
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/ # Flyway 迁移脚本
│   └── pom.xml           # Maven 构建（严格依赖白名单）
├── web/                  # Vue 3 前端（未改动）
├── legacy/go/            # 已归档的 Go 后端（仅供参考）
├── docker-compose.yml    # 本地开发用 PostgreSQL
└── README.md
```

## 开发指南

### 前置条件

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose（用于 PostgreSQL）

### 后端

```bash
# 启动 PostgreSQL
docker-compose up -d

# 构建并运行
cd server
./mvnw spring-boot:run

# 或构建 JAR 包
./mvnw clean package
java -jar target/genealogy-server-0.0.1-SNAPSHOT.jar

# 运行测试（无需在线 PG）
./mvnw test
```

### 健康检查

```bash
curl http://localhost:8080/healthz
# {"status":"ok"}
```

### 前端

参见 [web/README.md](web/README.md) 了解前端开发说明。

### 本地开发指南

完整的本地演示运行指南请参阅 **[docs/LOCAL.md](docs/LOCAL.md)** — 涵盖前置条件、数据库设置、环境变量及注册/登录流程。

## 当前状态

### 已实现
- ✅ Spring Boot 骨架（严格依赖白名单）
- ✅ `GET /healthz` 健康检查端点
- ✅ Flyway 迁移（schema 包含 persons、families、relationships、unions、users、stories）
- ✅ MyBatis 数据访问层
- ✅ JWT（JSON Web Token）认证（`POST /api/v1/auth/register`、`POST /api/v1/auth/login`）
- ✅ Person/Family/Relationship/Union CRUD
- ✅ 家族图谱查询 API
- ✅ 本地媒体上传
- ✅ 家族故事功能
- ✅ Vue 3 前端（支持 mock 数据）

### 尚未实现
- ❌ SSO / OAuth 第三方登录
- ❌ 族谱打印/导出（PDF/图片）
- ❌ 历史时点视图（asOf）
- ❌ 实时协作编辑

## 设计决策

- **仅使用 Java 后端** — Go 代码已归档至 `legacy/go/` 供参考
- **MyBatis 优于 JPA** — 显式 SQL，无 ORM 魔法
- **仅使用 Flyway** — 简单的版本化迁移
- **无分布式架构** — 不使用 Spring Cloud、Gateway、Config Server、消息中间件
- **模块化单体** — 无微服务复杂性的清晰架构

## 依赖策略

参见 [server/DEPENDENCY_ALLOWLIST.md](server/DEPENDENCY_ALLOWLIST.md) 了解严格的依赖白名单和拒绝规则。

## 门禁

合并前必须通过测试：
```bash
cd server && ./mvnw test
```
