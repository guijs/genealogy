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
 * - 认证：Authorization: Bearer <token>
 * - 成功后调方调 reloadGraph() 刷新树
 * - 不含 displayName 写入（服务端不接受）
 */
import { isUsingGraphApi } from './graphClient'
import { getAuthHeadersWithContentType, AuthRequiredError } from './auth'

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
  try {
    return getAuthHeadersWithContentType()
  } catch (err) {
    if (err instanceof AuthRequiredError) {
      throw new PersonApiError('请先登录', 401, 'AUTH_MISSING')
    }
    throw err
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

export interface HidePersonRequest {
  confirm_hide_with_active_union?: boolean
}

export class HidePersonConflictError extends Error {
  readonly status = 409
  readonly code = 'ACTIVE_UNION_REQUIRES_CONFIRM'

  constructor(message: string) {
    super(message)
    this.name = 'HidePersonConflictError'
  }
}

/**
 * 隐藏家族成员
 * POST /api/v1/families/{familyId}/persons/{personId}/hide
 *
 * @param confirmHideWithActiveUnion 若成员有活跃婚姻需确认才可隐藏
 * @returns void
 * @throws PersonApiError 404
 * @throws HidePersonConflictError 409 — 有活跃婚姻需确认
 */
export async function hidePerson(
  familyId: string,
  personId: string,
  confirmHideWithActiveUnion?: boolean,
): Promise<void> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/persons/${encodeURIComponent(personId)}/hide`

  const body: HidePersonRequest | undefined = confirmHideWithActiveUnion
    ? { confirm_hide_with_active_union: confirmHideWithActiveUnion }
    : undefined

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: body ? JSON.stringify(body) : undefined,
  })

  if (res.status === 409) {
    const data = await res.json().catch(() => ({ error: '有活跃婚姻，需确认后才能隐藏' }))
    throw new HidePersonConflictError(data.error ?? '有活跃婚姻，需确认后才能隐藏')
  }

  if (!res.ok) {
    throw PersonApiError.fromStatus(res.status, url)
  }
}

/**
 * 恢复已隐藏的家族成员
 * POST /api/v1/families/{familyId}/persons/{personId}/restore
 *
 * @returns void
 * @throws PersonApiError 404
 */
export async function restorePerson(
  familyId: string,
  personId: string,
): Promise<void> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/persons/${encodeURIComponent(personId)}/restore`

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw PersonApiError.fromStatus(res.status, url)
  }
}
