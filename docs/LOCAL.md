# 本地开发指南 / Local Development Guide

本文档帮助新成员在笔记本电脑上运行 Genealogy 演示环境。

## 目录

- [前置条件](#前置条件)
- [快速启动](#快速启动)
- [环境变量配置](#环境变量配置)
- [注册与登录](#注册与登录)
- [媒体文件存储](#媒体文件存储)
- [前端配置](#前端配置)
- [端口速查](#端口速查)
- [当前功能与非目标](#当前功能与非目标)

---

## 前置条件

| 依赖 | 版本要求 | 说明 |
|------|----------|------|
| **JDK** | 17+ | Spring Boot 3.x 要求 JDK 17 |
| **Maven** | 3.8+ | 或使用项目自带的 `./mvnw` |
| **Node.js** | 20+ | 推荐 LTS 版本（`@types/node` ^24 要求较新版本） |
| **npm** | 随 Node.js | 或 pnpm/yarn |
| **PostgreSQL** | 16 | 可用 Docker 快速启动（见下） |
| **Docker** | 可选 | 用于快速启动 PostgreSQL |

验证命令：

```bash
java -version          # openjdk 17.x.x 或更高
mvn -v                 # Apache Maven 3.8.x 或更高
node -v                # v20.x.x 或更高
docker --version       # Docker version 20.x 或更高（可选）
```

---

## 快速启动

### 1. 启动 PostgreSQL

**方式 A：使用 Docker Compose（推荐）**

项目根目录已提供 `docker-compose.yml`，仅包含 PostgreSQL：

```bash
# 在项目根目录
docker-compose up -d

# 检查状态
docker-compose ps
# genealogy-postgres   running   0.0.0.0:5432->5432/tcp
```

该配置自动创建数据库 `genealogy`、用户 `genealogy`、密码 `genealogy`。

**方式 B：手动安装 PostgreSQL**

如果已有本地 PostgreSQL 实例：

```bash
# 创建数据库和用户
psql -U postgres

CREATE USER genealogy WITH PASSWORD 'genealogy';
CREATE DATABASE genealogy OWNER genealogy;
\q
```

### 2. 启动后端（Spring Boot）

```bash
cd server

# 使用 Maven Wrapper 启动（首次会下载依赖）
./mvnw spring-boot:run

# 或构建 JAR 后运行
./mvnw clean package -DskipTests
java -jar target/genealogy-server-0.0.1-SNAPSHOT.jar
```

启动时 Flyway 会自动执行数据库迁移（`baseline-on-migrate: true`），无需手动运行 SQL。

**验证后端启动**：

```bash
curl http://localhost:8080/healthz
# {"status":"ok"}
```

### 3. 启动前端（Vite）

```bash
cd web

npm install
npm run dev
```

终端会显示本地地址，通常为：

```
  VITE v8.x.x  ready in xxx ms

  ➜  Local:   http://localhost:5173/
```

浏览器打开 `http://localhost:5173` 即可访问。

> **注意**：默认情况下前端使用 mock fixture 数据，不需要后端。如需连接真实 API，见 [前端配置](#前端配置)。

---

## 环境变量配置

后端环境变量（通过 `export` 设置）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/genealogy` | 数据库连接 URL |
| `SPRING_DATASOURCE_USERNAME` | `genealogy` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | `genealogy` | 数据库密码 |
| `JWT_SECRET` | 开发默认值（见下） | JWT 签名密钥（**生产环境必须更换**） |
| `MEDIA_LOCAL_ROOT` | `./data/media` | 本地媒体文件存储目录 |
| `MEDIA_UPLOAD_SECRET` | 开发默认值 | 媒体上传令牌签名密钥 |

### JWT_SECRET 配置

`application.yml` 中的默认值：

```yaml
jwt:
  secret: ${JWT_SECRET:change-me-in-production-min-32-chars-long-secret-key}
```

- 本地开发可使用默认值
- **生产环境必须设置至少 32 字符的随机字符串**

启动时覆盖示例：

```bash
export JWT_SECRET="your-secure-random-string-at-least-32-chars"
./mvnw spring-boot:run
```

---

## 注册与登录

认证使用 JWT Bearer Token，端点位于 `/api/v1/auth/`。

### 注册新用户

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "password123"}'
```

成功响应（201）：

```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 登录

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "password123"}'
```

成功响应（200）：

```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 使用 Token 访问受保护 API

```bash
curl http://localhost:8080/api/v1/families \
  -H "Authorization: Bearer <your-token>"
```

密码要求：至少 8 个字符。

---

## 媒体文件存储

媒体上传采用本地文件系统存储，配置在 `application.yml`：

```yaml
media:
  storage: local
  local:
    root: ${MEDIA_LOCAL_ROOT:./data/media}
```

- 默认存储在 `server/data/media/` 目录
- 确保运行用户对该目录有读写权限
- 目录不存在时会自动创建

自定义存储目录：

```bash
export MEDIA_LOCAL_ROOT="/path/to/your/media"
./mvnw spring-boot:run
```

---

## 前端配置

前端默认使用 mock fixture 数据，**无需后端即可运行**。

### 切换到真实 API

复制 `.env.example` 为 `.env.local`：

```bash
cd web
cp .env.example .env.local
```

编辑 `.env.local`：

```bash
# 启用真实 API
VITE_USE_GRAPH_API=true

# API 基址（后端地址）
VITE_GRAPH_API_BASE=http://localhost:8080
```

重启前端开发服务器使配置生效。

### 认证流程

前端使用 **JWT Bearer Token** 认证，流程如下：

1. 在前端登录页面输入邮箱和密码
2. 登录成功后，token 自动存储在 `localStorage`（键 `auth_token`）
3. 后续 API 请求自动携带 `Authorization: Bearer <token>` header

无需手动配置 token 或 user ID — 登录后前端会自动处理认证。

> **注意**：`.env.example` 中的 `VITE_GRAPH_USER_ID` 是历史遗留配置，已废弃不使用。

---

## 端口速查

| 服务 | 端口 | 配置位置 |
|------|------|----------|
| Spring Boot 后端 | 8080 | `server/src/main/resources/application.yml` |
| Vite 前端开发服务器 | 5173 | Vite 默认，可在 `vite.config.ts` 修改 |
| PostgreSQL | 5432 | `docker-compose.yml` |

---

## 当前功能与非目标

### ✅ 已实现

- Spring Boot 后端骨架（严格依赖白名单）
- `GET /healthz` 健康检查端点
- JWT 认证（注册/登录）
- Flyway 数据库迁移
- MyBatis 数据访问
- Person/Family/Relationship CRUD
- 家族图谱查询 API
- 本地媒体文件上传
- 家族故事（Stories）功能
- Vue 3 前端（mock 模式可独立运行）

### ❌ 非目标 / 未实现

- SSO / OAuth 第三方登录
- 族谱打印导出（PDF/图片）
- 历史时点视图（asOf）
- 实时协作编辑
- 移动端 App
- 微服务架构（当前为模块化单体）

---

## 故障排除

### 数据库连接失败

```
org.postgresql.util.PSQLException: Connection refused
```

检查 PostgreSQL 是否启动：

```bash
docker-compose ps
# 或
pg_isready -h localhost -p 5432
```

### Flyway 迁移失败

如果数据库已有表但未记录迁移历史：

```bash
# 清空数据库重新开始（仅开发环境！）
docker-compose down -v
docker-compose up -d
```

### 前端构建失败

```bash
# 清理缓存重装依赖
cd web
rm -rf node_modules package-lock.json
npm install
```

### JWT Token 无效

确保 `JWT_SECRET` 在后端启动时与签发 token 时一致。重启后端会导致旧 token 失效（如果使用了默认密钥）。

---

## 运行测试

```bash
# 后端测试（使用 H2 内存数据库，无需 PostgreSQL）
cd server
./mvnw test

# 前端测试
cd web
npm test
```
