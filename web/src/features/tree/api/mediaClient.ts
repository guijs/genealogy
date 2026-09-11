/**
 * Media Upload API client（B4）
 *
 * - POST /api/v1/families/{familyId}/media/upload-url — 获取预签名上传 URL
 *
 * 硬约束：
 * - Body snake_case：{ mime_type, file_size } — 绝不发送 storage_key（服务器会拒绝）
 * - 客户端预检 MIME：仅 jpeg/png/webp
 * - 客户端预检大小：>0 且 ≤5MB
 * - 仅在真 API 模式下可用（VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE）
 * - 认证：X-User-Id header = VITE_GRAPH_USER_ID
 * - 响应 { upload_url, storage_key }；调方需显示两者
 * - Mock 模式下不可伪造成功
 * - 不含整树写回
 * - Real PUT to upload_url 是可选的
 */
import { isUsingGraphApi } from './graphClient'

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
  const userId = (import.meta.env.VITE_GRAPH_USER_ID as string | undefined)?.trim()
  if (!userId) {
    throw new MediaApiError(
      '请先设置 VITE_GRAPH_USER_ID 环境变量',
      401,
      'AUTH_MISSING',
    )
  }
  return {
    'X-User-Id': userId,
    'Content-Type': 'application/json',
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
 * 上传文件到预签名 URL（可选）
 *
 * @param uploadUrl - 从 requestUploadUrl 获取的预签名 URL
 * @param file - 要上传的文件 Blob
 * @param mimeType - 文件 MIME 类型
 * @throws Error 上传失败
 */
export async function uploadToPresignedUrl(
  uploadUrl: string,
  file: Blob,
  mimeType: string,
): Promise<void> {
  const res = await fetch(uploadUrl, {
    method: 'PUT',
    headers: {
      'Content-Type': mimeType,
    },
    body: file,
  })

  if (!res.ok) {
    throw new Error(`上传失败: ${res.status} ${res.statusText}`)
  }
}
