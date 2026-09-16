/**
 * Person Reference Candidates API client
 *
 * - GET /api/v1/families/{familyId}/person-ref-candidates — list candidates for person picker
 *
 * Constraints:
 * - Auth: Authorization: Bearer <token>
 * - familyId in path
 * - Only available when real API mode is on (VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE)
 * - viewer sees non-hidden persons; admin/editor may see more
 * - deceased persons are included and can be referenced
 */
import { isUsingGraphApi } from './graphClient'
import { getAuthHeadersWithContentType, AuthRequiredError } from './auth'
import type { PersonRefCandidatesResponse } from './types'

export class PersonRefApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(
    message: string,
    status: number,
    code?: string,
  ) {
    super(message)
    this.name = 'PersonRefApiError'
    this.status = status
    this.code = code
  }

  static fromStatus(status: number): PersonRefApiError {
    const messages: Record<number, string> = {
      400: '请求参数无效',
      401: '请先登录',
      403: '没有访问权限',
      404: '未找到家族',
    }
    return new PersonRefApiError(
      messages[status] ?? `请求失败: ${status}`,
      status,
    )
  }

  static mockMode(): PersonRefApiError {
    return new PersonRefApiError(
      '人物引用功能需要连接真实 API（当前为 mock 模式）',
      0,
      'MOCK_MODE',
    )
  }

  static invalidPersonRef(): PersonRefApiError {
    return new PersonRefApiError(
      '引用的人物无效或不存在于当前家族',
      400,
      'INVALID_PERSON_REF',
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
      throw new PersonRefApiError('请先登录', 401, 'AUTH_MISSING')
    }
    throw err
  }
}

function ensureRealApi(): void {
  if (!isUsingGraphApi()) {
    throw PersonRefApiError.mockMode()
  }
}

/**
 * Fetch person reference candidates for a family.
 * GET /api/v1/families/{familyId}/person-ref-candidates
 *
 * @param familyId - The family ID (required)
 * @returns PersonRefCandidatesResponse { candidates: PersonRefCandidate[] }
 * @throws PersonRefApiError
 */
export async function fetchPersonRefCandidates(familyId: string): Promise<PersonRefCandidatesResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new PersonRefApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/person-ref-candidates`

  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw PersonRefApiError.fromStatus(res.status)
  }

  return (await res.json()) as PersonRefCandidatesResponse
}
