/**
 * Media Upload API client（B4）
 *
 * 两步上传流程：
 * 1. POST /api/v1/families/{familyId}/media/upload-url — 获取预签名上传 URL
 * 2. PUT  /api/v1/media/uploads/{token}              — 实际上传文件
 *
 * 硬约束：
 * - POST Body snake_case：{ mime_type, file_size } — 绝不发送 storage_key（服务器会拒绝）
 * - PUT 请求不带 Authorization 头 — 认证在 HMAC token 中
 * - 客户端预检 MIME：仅 jpeg/png/webp
 * - 客户端预检大小：>0 且 ≤5MB
 * - 仅在真 API 模式下可用（VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE）
 * - POST 需要 Authorization: Bearer <token>
 * - POST 响应 { upload_url, storage_key }
 * - Mock 模式下不可伪造成功
 * - 不含整树写回
 */
import { isUsingGraphApi } from './graphClient'
import { getAuthHeadersWithContentType, AuthRequiredError } from './auth'

/** 允许的 MIME 类型 */
export const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const
export type AllowedMimeType = (typeof ALLOWED_MIME_TYPES)[number]

/** 最大文件大小：5MB */
export const MAX_FILE_SIZE = 5 * 1024 * 1024

/** 最小文件大小：大于 0 */
export const MIN_FILE_SIZE = 1

export interface RequestUploadUrlRequest {
  mime_type: AllowedMimeType
  file_size: number
}

export interface UploadUrlResponse {
  upload_url: string
  storage_key: string
}

export type MediaValidationErrorCode =
  | 'INVALID_MIME_TYPE'
  | 'FILE_TOO_LARGE'
  | 'FILE_EMPTY'
  | 'AUTH_MISSING'
  | 'MOCK_MODE'
  | 'PUT_CONTENT_TYPE_MISMATCH'
  | 'PUT_INVALID_TOKEN'
  | 'PUT_OVERSIZED'
  | 'PUT_NETWORK_ERROR'
  | 'PUT_SERVER_ERROR'

export class MediaApiError extends Error {
  readonly status: number
  readonly code?: MediaValidationErrorCode | string

  constructor(message: string, status: number, code?: MediaValidationErrorCode | string) {
    super(message)
    this.name = 'MediaApiError'
    this.status = status
    this.code = code
  }

  static fromStatus(status: number, url: string): MediaApiError {
    const messages: Record<number, string> = {
      400: '请求参数无效',
      401: '需要认证',
      403: '无权限上传到此家族',
      404: '家族不存在',
      413: '文件过大',
    }
    return new MediaApiError(
      messages[status] ?? `请求失败: ${status} (${url})`,
      status,
    )
  }

  static invalidMimeType(mimeType: string): MediaApiError {
    return new MediaApiError(
      `不支持的文件类型: ${mimeType}。仅支持 JPEG、PNG、WebP 格式`,
      0,
      'INVALID_MIME_TYPE',
    )
  }

  static fileTooLarge(fileSize: number): MediaApiError {
    const sizeMB = (fileSize / (1024 * 1024)).toFixed(2)
    return new MediaApiError(
      `文件过大: ${sizeMB}MB。最大允许 5MB`,
      0,
      'FILE_TOO_LARGE',
    )
  }

  static fileEmpty(): MediaApiError {
    return new MediaApiError(
      '文件不能为空',
      0,
      'FILE_EMPTY',
    )
  }

  static putContentTypeMismatch(): MediaApiError {
    return new MediaApiError(
      '文件类型不匹配：请确保上传的文件类型与申请时一致',
      400,
      'PUT_CONTENT_TYPE_MISMATCH',
    )
  }

  static putInvalidToken(): MediaApiError {
    return new MediaApiError(
      '上传链接无效或已过期，请重新获取',
      401,
      'PUT_INVALID_TOKEN',
    )
  }

  static putOversized(): MediaApiError {
    return new MediaApiError(
      '文件过大，超出服务器限制',
      413,
      'PUT_OVERSIZED',
    )
  }

  static putNetworkError(detail?: string): MediaApiError {
    return new MediaApiError(
      detail ? `网络错误：${detail}` : '网络错误，请检查网络连接后重试',
      0,
      'PUT_NETWORK_ERROR',
    )
  }

  static putServerError(status: number): MediaApiError {
    return new MediaApiError(
      `服务器错误 (${status})，请稍后重试`,
      status,
      'PUT_SERVER_ERROR',
    )
  }
}

/**
 * 验证 MIME 类型是否允许
 */
export function isValidMimeType(mimeType: string): mimeType is AllowedMimeType {
  return ALLOWED_MIME_TYPES.includes(mimeType as AllowedMimeType)
}

/**
 * 验证文件大小是否合法
 */
export function isValidFileSize(fileSize: number): boolean {
  return fileSize >= MIN_FILE_SIZE && fileSize <= MAX_FILE_SIZE
}

/**
 * 客户端预检：验证文件 MIME 类型和大小
 * @throws MediaApiError 若验证失败
 */
export function validateFile(mimeType: string, fileSize: number): void {
  if (fileSize < MIN_FILE_SIZE) {
    throw MediaApiError.fileEmpty()
  }
  if (fileSize > MAX_FILE_SIZE) {
    throw MediaApiError.fileTooLarge(fileSize)
  }
  if (!isValidMimeType(mimeType)) {
    throw MediaApiError.invalidMimeType(mimeType)
  }
}

function apiBase(): string {
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return base ? base.replace(/\/$/, '') : ''
}

function authHeaders(): HeadersInit {
  try {
    return getAuthHeadersWithContentType()
  } catch (err) {
    if (err instanceof AuthRequiredError) {
      throw new MediaApiError('请先登录', 401, 'AUTH_MISSING')
    }
    throw err
  }
}

function ensureRealApi(): void {
  if (!isUsingGraphApi()) {
    throw new MediaApiError(
      '上传媒体文件需要连接真实 API（当前为 mock 模式）',
      0,
      'MOCK_MODE',
    )
  }
}

/**
 * 请求预签名上传 URL
 * POST /api/v1/families/{familyId}/media/upload-url
 *
 * 客户端预检 MIME（jpeg/png/webp）和大小（>0, ≤5MB）后调用。
 * Body 仅含 { mime_type, file_size }，绝不发送 storage_key。
 *
 * @param familyId - 家族 ID（路径参数）
 * @param mimeType - 文件 MIME 类型（必须为 jpeg/png/webp）
 * @param fileSize - 文件大小（字节，>0 且 ≤5MB）
 * @returns UploadUrlResponse { upload_url, storage_key }
 * @throws MediaApiError 验证失败 / 401 / 403 / 404
 */
export async function requestUploadUrl(
  familyId: string,
  mimeType: string,
  fileSize: number,
): Promise<UploadUrlResponse> {
  ensureRealApi()

  validateFile(mimeType, fileSize)

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/media/upload-url`

  const body: RequestUploadUrlRequest = {
    mime_type: mimeType as AllowedMimeType,
    file_size: fileSize,
  }

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    throw MediaApiError.fromStatus(res.status, url)
  }

  return (await res.json()) as UploadUrlResponse
}

/**
 * 解析上传 URL，处理相对路径
 * 如果 uploadUrl 是相对路径，基于 VITE_GRAPH_API_BASE 解析
 */
function resolveUploadUrl(uploadUrl: string): string {
  if (uploadUrl.startsWith('http://') || uploadUrl.startsWith('https://')) {
    return uploadUrl
  }
  const base = apiBase()
  if (!base) {
    return uploadUrl
  }
  if (uploadUrl.startsWith('/')) {
    return `${base}${uploadUrl}`
  }
  return `${base}/${uploadUrl}`
}

/**
 * PUT 上传文件到预签名 URL
 *
 * PUT /api/v1/media/uploads/{token}
 *
 * 特点：
 * - 不带 Authorization 头 — 认证在 HMAC token 中
 * - Content-Type 必须与申请时的 mime_type 完全匹配
 * - 浏览器自动设置 Content-Length
 *
 * @param uploadUrl - 从 requestUploadUrl 获取的 upload_url
 * @param file - 要上传的文件（File 或 Blob）
 * @returns Promise<void> 成功时返回
 * @throws MediaApiError
 *   - PUT_CONTENT_TYPE_MISMATCH (400) - Content-Type 不匹配
 *   - PUT_INVALID_TOKEN (401) - token 无效或过期
 *   - PUT_OVERSIZED (413) - 文件超大
 *   - PUT_SERVER_ERROR (5xx) - 服务器错误
 *   - PUT_NETWORK_ERROR - 网络错误
 */
export async function putUploadFile(
  uploadUrl: string,
  file: File | Blob,
): Promise<void> {
  const resolvedUrl = resolveUploadUrl(uploadUrl)

  let res: Response
  try {
    res = await fetch(resolvedUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': file.type,
      },
      body: file,
    })
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    throw MediaApiError.putNetworkError(message)
  }

  if (res.ok) {
    return
  }

  switch (res.status) {
    case 400:
      throw MediaApiError.putContentTypeMismatch()
    case 401:
      throw MediaApiError.putInvalidToken()
    case 413:
      throw MediaApiError.putOversized()
    default:
      if (res.status >= 500) {
        throw MediaApiError.putServerError(res.status)
      }
      throw new MediaApiError(
        `上传失败: ${res.status} ${res.statusText}`,
        res.status,
      )
  }
}

/**
 * @deprecated 使用 putUploadFile 替代
 */
export const uploadToPresignedUrl = putUploadFile
