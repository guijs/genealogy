/**
 * Family member management API client
 *
 * - GET /api/v1/families/{familyId}/members — list members
 * - POST /api/v1/families/{familyId}/members — add member
 * - PATCH /api/v1/families/{familyId}/members/{userId} — update member role
 * - DELETE /api/v1/families/{familyId}/members/{userId} — remove member
 *
 * Constraints:
 * - Auth: X-User-Id header = VITE_GRAPH_USER_ID
 * - familyId in path (from route query)
 * - Only available when real API mode is on (VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE)
 * - Mock mode: cannot fake write/list success pretending persistence
 * - PATCH/DELETE require admin role (403 otherwise)
 * - 409 returned when trying to remove/demote the last admin
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

export interface UpdateMemberRoleRequest {
  role: MemberRole
}

export interface UpdateMemberRoleResponse {
  user_id: string
  role: MemberRole
}

export type ErrorContext = 'add' | 'updateRole' | 'remove'

export class MemberApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'MemberApiError'
    this.status = status
    this.code = code
  }

  static fromStatus(status: number, url: string, context?: ErrorContext): MemberApiError {
    const messages: Record<number, string> = {
      400: '请求参数无效',
      401: '需要认证',
      403: '需要管理员权限',
      409: '该用户已是家族成员',
    }
    if (status === 409 && (context === 'updateRole' || context === 'remove')) {
      return new MemberApiError('不能移除或降级最后一个管理员', status, 'LAST_ADMIN')
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
    throw MemberApiError.fromStatus(res.status, url, 'add')
  }

  return (await res.json()) as AddMemberResponse
}

/**
 * Update a member's role
 * PATCH /api/v1/families/{familyId}/members/{userId}
 *
 * @param familyId - The family ID
 * @param userId - The user ID to update
 * @param role - The new role
 * @returns UpdateMemberRoleResponse { user_id, role }
 * @throws MemberApiError 400/401/403/409
 */
export async function updateMemberRole(
  familyId: string,
  userId: string,
  role: MemberRole,
): Promise<UpdateMemberRoleResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new MemberApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!userId) {
    throw new MemberApiError('userId 不能为空', 400, 'INVALID_USER_ID')
  }

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/members/${encodeURIComponent(userId)}`

  const res = await fetch(url, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ role }),
  })

  if (!res.ok) {
    throw MemberApiError.fromStatus(res.status, url, 'updateRole')
  }

  return (await res.json()) as UpdateMemberRoleResponse
}

/**
 * Remove a member from a family
 * DELETE /api/v1/families/{familyId}/members/{userId}
 *
 * @param familyId - The family ID
 * @param userId - The user ID to remove
 * @throws MemberApiError 401/403/404/409
 */
export async function removeMember(
  familyId: string,
  userId: string,
): Promise<void> {
  ensureRealApi()

  if (!familyId) {
    throw new MemberApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!userId) {
    throw new MemberApiError('userId 不能为空', 400, 'INVALID_USER_ID')
  }

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/members/${encodeURIComponent(userId)}`

  const res = await fetch(url, {
    method: 'DELETE',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw MemberApiError.fromStatus(res.status, url, 'remove')
  }
}
