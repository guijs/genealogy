# ADR-001: Local Disk Object Storage for Media Upload

**Status:** Accepted  
**Date:** 2026-09-15  
**Context:** B4 Real Object Storage Implementation

## Decision

Use **local disk storage** as the initial implementation of the `ObjectStore` interface for media file uploads.

## Context

The genealogy application needs real object storage to replace the `StubObjectStore` that returns fake URLs. The upload-url endpoint (`POST /api/v1/families/{familyId}/media/upload-url`) must return a URL that clients can actually use to upload files via HTTP PUT.

## Decision Drivers

- Zero additional infrastructure dependencies
- Works offline and in local development
- Maintains the same `ObjectStore` interface for future S3/MinIO migration
- No new Maven dependencies required

## Alternatives Considered

### 1. MinIO/S3 SDK Now
**Pros:** Production-ready, S3-compatible, industry standard  
**Cons:** Requires MinIO container or AWS credentials, adds `aws-sdk` dependency, increases complexity for local dev

### 2. Local Disk (Chosen)
**Pros:** Zero deps, works offline, simple to reason about, easy testing  
**Cons:** Not suitable for multi-instance deployment (requires shared filesystem or migration)

### 3. Embedded Object Store (e.g., H2-like)
**Pros:** Single JAR deployment  
**Cons:** No good Java options exist; would require custom implementation

## Implementation

### Upload Flow

1. Client calls `POST /api/v1/families/{familyId}/media/upload-url`
2. Server generates a storage key: `families/{familyId}/media/{uuid}.{ext}`
3. Server creates a signed upload token (HMAC) containing:
   - `storageKey` - where to store the file
   - `mimeType` - expected Content-Type
   - `maxSize` - maximum allowed bytes
   - `familyId` - for audit/validation
   - `exp` - expiry timestamp (15 minutes)
4. Server returns `upload_url` pointing to: `PUT /api/v1/media/uploads/{token}`
5. Client PUTs file bytes to the upload URL
6. Server validates token, Content-Type, Content-Length
7. Server writes file to `{media.local.root}/{storageKey}`

### Configuration

```yaml
media:
  storage: local                    # "local" (default) | "s3" (future)
  upload-secret: <base64-secret>    # HMAC signing key for upload tokens
  local:
    root: ./data/media              # local storage root directory
```

### Security

- Upload URLs are time-limited (15 minutes by default)
- HMAC signature prevents token tampering
- Token binds file type and size - cannot upload different content
- Upload endpoint does not require family membership (token is proof of authorization)

## Trade-offs

| Aspect | Local Disk | S3/MinIO |
|--------|------------|----------|
| Setup complexity | None | Requires container/credentials |
| Multi-instance | Needs shared FS | Native support |
| Cost | Free | Storage + transfer costs |
| Offline dev | Works | Requires local MinIO |
| CDN integration | Manual | Native |

## Migration Path to S3

The `ObjectStore` interface is designed for easy adapter swapping:

1. Add `S3ObjectStore implements ObjectStore` with `@ConditionalOnProperty(name = "media.storage", havingValue = "s3")`
2. Add AWS SDK dependency to pom.xml
3. Configure S3 bucket and credentials
4. Set `media.storage=s3` in production

No API changes required - clients continue using the same upload-url endpoint.

## Consequences

- Local development and testing work without external services
- Single-instance deployment is fully functional
- Multi-instance deployment requires either shared filesystem or S3 migration
- Clear upgrade path to S3/MinIO when needed
