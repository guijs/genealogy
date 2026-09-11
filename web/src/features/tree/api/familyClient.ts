/**
 * Family 创建 API client
 *
 * - POST /api/v1/families — 创建家族
 *
 * 硬约束：
 * - Body: { name: string }
 * - 仅在真 API 模式下可用（VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE）
 * - 认证：X-User-Id header = VITE_GRAPH_USER_ID
 * - 成功后返回 FamilyResponse { id, name }
 * - Mock 模式下不可伪造成功（与 personClient / graphClient 保持一致）
 */
import { isUsingGraphApi } from './graphClient'

export interface CreateFamilyRequest {
  name: string
}

export interface FamilyResponse {
  id: string
  name: string
}

export interface FamiliesListResponse {
  families: FamilyResponse[]
}

export class FamilyApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'FamilyApiError'
    this.status = status
    this.code = code
  }

  static fromStatus(status: number, url: string): FamilyApiError {
    const messages: Record<number, string> = {
      400: '家族名称无效',
      401: '需要认证',
    }
    return new FamilyApiError(
      messages[status] ?? `请求失败: ${status} (${url})`,
      status,
    )
  }
}

function apiBase(): string {
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return base ? base.replace(/\/$/, '') : ''
}

function authHeaders(): HeadersInit {
  const userId = (import.meta.env.VITE_GRAPH_USER_ID as string | undefined)?.trim()
  if (!userId) {
    throw new FamilyApiError(
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
    throw new FamilyApiError(
      '创建家族需要连接真实 API（当前为 mock 模式）',
      0,
      'MOCK_MODE',
    )
  }
}

/**
 * 获取用户家族列表
 * GET /api/v1/families
 *
 * @returns FamiliesListResponse { families: FamilyResponse[] }
 * @throws FamilyApiError 401
 */
export async function listFamilies(): Promise<FamiliesListResponse> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families`

  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw FamilyApiError.fromStatus(res.status, url)
  }

  return (await res.json()) as FamiliesListResponse
}

/**
 * 创建家族
 * POST /api/v1/families
 *
 * @returns FamilyResponse { id, name }
 * @throws FamilyApiError 400/401
 */
export async function createFamily(
  data: CreateFamilyRequest,
): Promise<FamilyResponse> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families`

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    throw FamilyApiError.fromStatus(res.status, url)
  }

  return (await res.json()) as FamilyResponse
}
