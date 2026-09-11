/**
 * Union 写入 API client（Phase2-B）
 *
 * - POST /api/v1/families/{familyId}/unions — 创建婚姻关系
 * - POST /api/v1/families/{familyId}/unions/{unionId}/end — 结束婚姻关系
 *
 * 硬约束：
 * - Body snake_case：{ partner_a_id, partner_b_id, started_at? }
 * - 仅在真 API 模式下可用（VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE）
 * - 认证：X-User-Id header = VITE_GRAPH_USER_ID
 * - 成功后调方调 reloadGraph() 刷新树
 * - 422 dual-active 返回中文提示
 */
import { isUsingGraphApi } from './graphClient'

/** POST /api/v1/families/{familyId}/unions 请求体 (snake_case) */
export interface CreateUnionRequest {
  partner_a_id: string
  partner_b_id: string
  started_at?: string | null
}

/** POST /api/v1/families/{familyId}/unions/{unionId}/end 请求体 (snake_case) */
export interface EndUnionRequest {
  ended_reason?: string | null
  ended_at?: string | null
}

/** Union API 响应 (camelCase from server) */
export interface UnionResponse {
  id: string
  partnerAId: string
  partnerBId: string
  status: string
  startedAt?: string | null
  endedAt?: string | null
  endedReason?: string | null
}

export class UnionApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'UnionApiError'
    this.status = status
    this.code = code
  }

  static fromStatus(status: number, url: string, responseText?: string): UnionApiError {
    const messages: Record<number, string> = {
      400: '参数格式无效',
      403: '无权限执行此操作',
      404: '成员或家族不存在',
      422: '无法创建婚姻关系',
    }

    if (status === 422 && responseText) {
      if (responseText.includes('active union') || responseText.includes('already has')) {
        return new UnionApiError(
          '该成员已有存续婚姻，请先结束当前婚姻再添加新配偶',
          status,
          'DUAL_ACTIVE_UNION',
        )
      }
      if (responseText.includes('oneself')) {
        return new UnionApiError(
          '不能与自己建立婚姻关系',
          status,
          'SELF_UNION',
        )
      }
      if (responseText.includes('already ended')) {
        return new UnionApiError(
          '该婚姻关系已结束',
          status,
          'ALREADY_ENDED',
        )
      }
    }

    return new UnionApiError(
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
    throw new UnionApiError(
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
    throw new UnionApiError(
      '添加/结束婚姻需要连接真实 API（当前为 mock 模式）',
      0,
      'MOCK_MODE',
    )
  }
}

/**
 * 创建婚姻关系
 * POST /api/v1/families/{familyId}/unions
 *
 * @param familyId 家族 ID
 * @param partnerAId 配偶 A 的 personId
 * @param partnerBId 配偶 B 的 personId
 * @param startedAt 可选的婚姻开始日期
 * @returns UnionResponse
 * @throws UnionApiError 400/404/422
 */
export async function createUnion(
  familyId: string,
  partnerAId: string,
  partnerBId: string,
  startedAt?: string | null,
): Promise<UnionResponse> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/unions`

  const body: CreateUnionRequest = {
    partner_a_id: partnerAId,
    partner_b_id: partnerBId,
  }
  if (startedAt) {
    body.started_at = startedAt
  }

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    const errText = await res.text().catch(() => '')
    throw UnionApiError.fromStatus(res.status, url, errText)
  }

  return (await res.json()) as UnionResponse
}

/**
 * 结束婚姻关系
 * POST /api/v1/families/{familyId}/unions/{unionId}/end
 *
 * @param familyId 家族 ID
 * @param unionId 婚姻关系 ID
 * @param endedReason 可选的结束原因（如 'divorced', 'widowed'）
 * @param endedAt 可选的结束日期
 * @returns UnionResponse
 * @throws UnionApiError 404/422
 */
export async function endUnion(
  familyId: string,
  unionId: string,
  endedReason?: string | null,
  endedAt?: string | null,
): Promise<UnionResponse> {
  ensureRealApi()

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/unions/${encodeURIComponent(unionId)}/end`

  const body: EndUnionRequest = {}
  if (endedReason) {
    body.ended_reason = endedReason
  }
  if (endedAt) {
    body.ended_at = endedAt
  }

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    const errText = await res.text().catch(() => '')
    throw UnionApiError.fromStatus(res.status, url, errText)
  }

  return (await res.json()) as UnionResponse
}
