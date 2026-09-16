# 依赖白名单

本文档定义了 genealogy-server 项目的严格依赖白名单。

## 允许的依赖

| 依赖 | 用途 |
|------|------|
| `spring-boot-starter-web` | REST API、内嵌 Tomcat |
| `spring-boot-starter-validation` | Bean 验证（JSR-380） |
| `spring-boot-starter-test` | 测试（JUnit 5、Mockito 等） |
| `mybatis-spring-boot-starter` | MyBatis ORM 集成 |
| `postgresql` | PostgreSQL JDBC 驱动 |
| `flyway-core` | 数据库迁移 |
| `flyway-database-postgresql` | Flyway PostgreSQL 方言（Boot 3.x） |
| Boot 默认日志 | 通过 spring-boot-starter 的 Logback |
| `h2`（仅测试作用域） | 单元测试用内存数据库 |

## 禁止的依赖

以下依赖在本项目中**明确禁止**：

### ORM / 数据访问
- ❌ `spring-boot-starter-data-jpa` — 仅使用 MyBatis
- ❌ `hibernate-*` — 禁止 Hibernate
- ❌ `spring-data-*` — 禁止 Spring Data

### 数据库迁移
- ❌ `liquibase-*` — 仅使用 Flyway

### 企业级 / 分布式架构
- ❌ `spring-cloud-*` — 禁止 Spring Cloud
- ❌ `spring-cloud-starter-gateway` — 禁止 API Gateway
- ❌ `spring-cloud-config-*` — 禁止 Config Server
- ❌ `spring-cloud-starter-netflix-eureka-*` — 禁止 Eureka
- ❌ `spring-cloud-starter-alibaba-nacos-*` — 禁止 Nacos
- ❌ `spring-cloud-starter-openfeign` — 禁止 OpenFeign
- ❌ `spring-kafka` / `spring-rabbit` — 禁止消息中间件
- ❌ `spring-data-elasticsearch` — 禁止 Elasticsearch
- ❌ `seata-*` / `atomikos-*` — 禁止分布式事务

## 设计理念

本项目采用**模块化单体（Modular Monolith）**架构。我们有意保持最小的依赖足迹，以降低复杂性、提升启动速度、并维护简单的部署模型。

任何新依赖在引入前必须经过审查并添加到此白名单中。
