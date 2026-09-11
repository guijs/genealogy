/**
 * Person API client（Phase2-A）
 *
 * - GET /api/v1/families/{familyId}/persons — 获取家族成员列表
 * - POST /api/v1/families/{familyId}/persons — 创建成员
 * - PATCH /api/v1/families/{familyId}/persons/{personId} — 更新成员
 *
 * 硬约束：
 * - Body snake_case：{ first_name, last_name }
 * - 仅在真 API 模式下可用（VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE）
 * - 认证：X-User-Id header = VITE_GRAPH_USER_ID
 * - 成功后调方调 reloadGraph() 刷新树
 * - 不含 displayName 写入（服务端不接受）
 */
import { isUsingGraphApi } from './graphClient'

export interface CreatePersonRequest {
  first_name: string
  last_name: string
}

export interface UpdatePersonRequest {
  first_name?: string
  last_name?: string
}

export interface PersonResponse {
  id: string
  first_name: string
  last_name: string
}

export class PersonApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'PersonApiError'
    this.status = status
    this.code = code
  }

  static fromStatus(status: number, url: string): PersonApiError {
    const messages: Record<number, string> = {
      400: '姓名格式无效',
      404: '成员或家族不存在',
      409: '已达成员上限',
    }
    return new PersonApiError(
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
    throw new PersonApiError(
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
    throw new PersonApiError(
      '添加/编辑成员需要连接真实 API（当前为 mock 模式）',
      0,
      'MOCK_MODE',
    )
  }
}

export interface PersonsListResponse {
  persons: PersonResponse[]
}

/**
 * 获取家族成员列表
 * GET /api/v1/families/{familyId}/persons
 *
 * @returns PersonsListResponse { persons: PersonResponse[] }
 * @throws PersonApiError 401/404
 */
export async function listPersons(
  familyId: string,
): Promise<PersonsListResponse> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/persons`

  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw PersonApiError.fromStatus(res.status, url)
  }

  return (await res.json()) as PersonsListResponse
}

/**
 * 创建家族成员
 * POST /api/v1/families/{familyId}/persons
 *
 * @returns PersonResponse { id, first_name, last_name }
 * @throws PersonApiError 400/404/409
 */
export async function createPerson(
  familyId: string,
  data: CreatePersonRequest,
): Promise<PersonResponse> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/persons`

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    throw PersonApiError.fromStatus(res.status, url)
  }

  return (await res.json()) as PersonResponse
}

/**
 * 更新家族成员
 * PATCH /api/v1/families/{familyId}/persons/{personId}
 *
 * @returns PersonResponse { id, first_name, last_name }
 * @throws PersonApiError 400/404
 */
export async function updatePerson(
  familyId: string,
  personId: string,
  data: UpdatePersonRequest,
): Promise<PersonResponse> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/persons/${encodeURIComponent(personId)}`

  const res = await fetch(url, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    throw PersonApiError.fromStatus(res.status, url)
  }

  return (await res.json()) as PersonResponse
}
