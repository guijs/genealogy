/**
 * Relationship API client (AP-R14)
 *
 * - POST /api/v1/families/{familyId}/relationships/{id}/dissolve — 解除亲子关系
 * - POST /api/v1/families/{familyId}/relationships/{id}/restore — 恢复亲子关系
 *
 * 契约：
 * - 200 { id, dissolved }
 * - 409 already dissolved / not dissolved
 * - 422 restore blocked (cardinality/cycle/self-loop)
 * - 401/403/404 as usual
 *
 * 硬约束：
 * - 仅在真 API 模式下可用（VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE）
 * - 认证：Authorization: Bearer <token>
 * - 成功后调方调 reloadGraph() 刷新树
 */
import { isUsingGraphApi } from './graphClient'
import { getAuthHeaders, AuthRequiredError } from './auth'
import type { DissolveRestoreResponse } from './types'

export class RelationshipApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'RelationshipApiError'
    this.status = status
    this.code = code
  }
}

export class AlreadyDissolvedException extends RelationshipApiError {
  constructor() {
    super('关系已解除', 409, 'ALREADY_DISSOLVED')
  }
}

export class NotDissolvedException extends RelationshipApiError {
  constructor() {
    super('关系未解除', 409, 'NOT_DISSOLVED')
  }
}

export class RestoreBlockedException extends RelationshipApiError {
  constructor(message: string) {
    super(message, 422, 'RESTORE_BLOCKED')
  }
}

function apiBase(): string {
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return base ? base.replace(/\/$/, '') : ''
}

function authHeaders(): HeadersInit {
  try {
    return getAuthHeaders()
  } catch (err) {
    if (err instanceof AuthRequiredError) {
      throw new RelationshipApiError('请先登录', 401, 'AUTH_MISSING')
    }
    throw err
  }
}

function ensureRealApi(): void {
  if (!isUsingGraphApi()) {
    throw new RelationshipApiError(
      '解除/恢复亲子关系需要连接真实 API（当前为 mock 模式）',
      0,
      'MOCK_MODE',
    )
  }
}

/**
 * 解除亲子关系
 * POST /api/v1/families/{familyId}/relationships/{relationshipId}/dissolve
 *
 * @returns DissolveRestoreResponse { id, dissolved: true }
 * @throws RelationshipApiError 401/403/404
 * @throws AlreadyDissolvedException 409
 */
export async function dissolveRelationship(
  familyId: string,
  relationshipId: string,
): Promise<DissolveRestoreResponse> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/relationships/${encodeURIComponent(relationshipId)}/dissolve`

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
  })

  if (res.status === 409) {
    throw new AlreadyDissolvedException()
  }

  if (res.status === 403) {
    throw new RelationshipApiError('没有编辑权限', 403, 'FORBIDDEN')
  }

  if (res.status === 404) {
    throw new RelationshipApiError('关系不存在', 404, 'NOT_FOUND')
  }

  if (!res.ok) {
    throw new RelationshipApiError(`请求失败: ${res.status}`, res.status)
  }

  return (await res.json()) as DissolveRestoreResponse
}

/**
 * 恢复亲子关系
 * POST /api/v1/families/{familyId}/relationships/{relationshipId}/restore
 *
 * @returns DissolveRestoreResponse { id, dissolved: false }
 * @throws RelationshipApiError 401/403/404
 * @throws NotDissolvedException 409
 * @throws RestoreBlockedException 422
 */
export async function restoreRelationship(
  familyId: string,
  relationshipId: string,
): Promise<DissolveRestoreResponse> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/relationships/${encodeURIComponent(relationshipId)}/restore`

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
  })

  if (res.status === 409) {
    throw new NotDissolvedException()
  }

  if (res.status === 422) {
    const data = await res.json().catch(() => ({ error: '恢复失败：可能存在冲突' }))
    throw new RestoreBlockedException(data.error ?? '恢复失败：可能存在冲突')
  }

  if (res.status === 403) {
    throw new RelationshipApiError('没有编辑权限', 403, 'FORBIDDEN')
  }

  if (res.status === 404) {
    throw new RelationshipApiError('关系不存在', 404, 'NOT_FOUND')
  }

  if (!res.ok) {
    throw new RelationshipApiError(`请求失败: ${res.status}`, res.status)
  }

  return (await res.json()) as DissolveRestoreResponse
}
