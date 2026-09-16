# ADR-001：媒体上传的本地磁盘对象存储

**状态：** 已采纳  
**日期：** 2026-09-15  
**背景：** B4 真实对象存储实现

## 决策

使用**本地磁盘存储**作为媒体文件上传 `ObjectStore` 接口的初始实现。

## 背景

家谱应用需要真实的对象存储来替换返回假 URL 的 `StubObjectStore`。上传 URL 端点（`POST /api/v1/families/{familyId}/media/upload-url`）必须返回客户端可以实际使用 HTTP PUT 上传文件的 URL。

## 决策驱动因素

- 零额外基础设施依赖
- 支持离线和本地开发
- 保持与 `ObjectStore` 接口一致，便于未来迁移到 S3/MinIO
- 无需新增 Maven 依赖

## 考虑的备选方案

### 1. 立即使用 MinIO/S3 SDK
**优点：** 生产就绪、S3 兼容、行业标准  
**缺点：** 需要 MinIO 容器或 AWS 凭证，增加 `aws-sdk` 依赖，增加本地开发复杂度

### 2. 本地磁盘（已选择）
**优点：** 零依赖、支持离线、易于理解、便于测试  
**缺点：** 不适合多实例部署（需要共享文件系统或迁移）

### 3. 嵌入式对象存储（如类 H2 方案）
**优点：** 单 JAR 部署  
**缺点：** Java 生态没有成熟方案；需要自定义实现

## 实现

### 上传流程

1. 客户端调用 `POST /api/v1/families/{familyId}/media/upload-url`
2. 服务端生成存储键：`families/{familyId}/media/{uuid}.{ext}`
3. 服务端创建签名上传令牌（HMAC），包含：
   - `storageKey` - 文件存储位置
   - `mimeType` - 预期 Content-Type
   - `maxSize` - 允许的最大字节数
   - `familyId` - 用于审计/验证
   - `exp` - 过期时间戳（15 分钟）
4. 服务端返回指向 `PUT /api/v1/media/uploads/{token}` 的 `upload_url`
5. 客户端将文件字节 PUT 到上传 URL
6. 服务端验证令牌、Content-Type、Content-Length
7. 服务端将文件写入 `{media.local.root}/{storageKey}`

### 配置

```yaml
media:
  storage: local                    # "local"（默认）| "s3"（未来）
  upload-secret: <base64-secret>    # 上传令牌的 HMAC 签名密钥
  local:
    root: ./data/media              # 本地存储根目录
```

### 安全性

- 上传 URL 有时间限制（默认 15 分钟）
- HMAC 签名防止令牌篡改
- 令牌绑定文件类型和大小 — 无法上传不同内容
- 上传端点不需要家族成员身份（令牌即授权证明）

## 权衡

| 方面 | 本地磁盘 | S3/MinIO |
|------|----------|----------|
| 配置复杂度 | 无 | 需要容器/凭证 |
| 多实例支持 | 需要共享文件系统 | 原生支持 |
| 成本 | 免费 | 存储 + 流量费用 |
| 离线开发 | 可用 | 需要本地 MinIO |
| CDN 集成 | 手动 | 原生支持 |

## 迁移到 S3 的路径

`ObjectStore` 接口设计便于轻松切换适配器：

1. 添加 `S3ObjectStore implements ObjectStore`，使用 `@ConditionalOnProperty(name = "media.storage", havingValue = "s3")`
2. 在 pom.xml 中添加 AWS SDK 依赖
3. 配置 S3 存储桶和凭证
4. 在生产环境设置 `media.storage=s3`

无需 API 变更 — 客户端继续使用相同的 upload-url 端点。

## 后果

- 本地开发和测试无需外部服务即可工作
- 单实例部署完全可用
- 多实例部署需要共享文件系统或迁移到 S3
- 有清晰的 S3/MinIO 升级路径
