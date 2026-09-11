/**
 * Family member management API client
 *
 * - GET /api/v1/families/{familyId}/members — list members
 * - POST /api/v1/families/{familyId}/members — add member
 *
 * Constraints:
 * - Auth: X-User-Id header = VITE_GRAPH_USER_ID
 * - familyId in path (from route query)
 * - Only available when real API mode is on (VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE)
 * - Mock mode: cannot fake write/list success pretending persistence
 * - NO role change PATCH, NO remove member (backend not ready)
 */
import { isUsingGraphApi } from './graphClient'

export type MemberRole = 'admin' | 'editor' | 'viewer'

export interface FamilyMember {
  user_id: string
  role: MemberRole
}

export interface MembersListResponse {
  members: FamilyMember[]
}

export interface AddMemberRequest {
  user_id: string
  role: MemberRole
}

export interface AddMemberResponse {
  user_id: string
  role: MemberRole
}

export class MemberApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'MemberApiError'
    this.status = status
    this.code = code
  }

  static fromStatus(status: number, url: string): MemberApiError {
    const messages: Record<number, string> = {
      400: '请求参数无效',
      401: '需要认证',
      403: '需要管理员权限',
      409: '该用户已是家族成员',
    }
    return new MemberApiError(
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
    throw new MemberApiError(
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
    throw new MemberApiError(
      '成员管理需要连接真实 API（当前为 mock 模式）',
      0,
      'MOCK_MODE',
    )
  }
}

/**
 * List family members
 * GET /api/v1/families/{familyId}/members
 *
 * @param familyId - The family ID
 * @returns MembersListResponse { members: FamilyMember[] }
 * @throws MemberApiError
 */
export async function listMembers(familyId: string): Promise<MembersListResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new MemberApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/members`

  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw MemberApiError.fromStatus(res.status, url)
  }

  return (await res.json()) as MembersListResponse
}

/**
 * Add a member to a family
 * POST /api/v1/families/{familyId}/members
 *
 * @param familyId - The family ID
 * @param data - { user_id: string, role: MemberRole }
 * @returns AddMemberResponse { user_id, role }
 * @throws MemberApiError 400/401/403/409
 */
export async function addMember(
  familyId: string,
  data: AddMemberRequest,
): Promise<AddMemberResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new MemberApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/members`

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    throw MemberApiError.fromStatus(res.status, url)
  }

  return (await res.json()) as AddMemberResponse
}
