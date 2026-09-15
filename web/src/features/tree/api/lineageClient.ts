/**
 * P1 Lineage projection API client.
 *
 * GET /api/v1/families/{familyId}/lineage
 * PUT /api/v1/families/{familyId}/progenitor
 *
 * Bearer auth required.
 *
 * Returns progenitor-based lineage layers (snake_case):
 * - Progenitor (始祖) at index 1 (第1世)
 * - Descendants at increasing indices (+2, +3, ...)
 *
 * Biological edges only; adoptive/dissolved edges excluded.
 * Hidden persons excluded.
 * Unconnected persons (not reachable from progenitor) excluded.
 */
import type { LineageResponse, SetProgenitorRequest, SetProgenitorResponse } from './types'
import { getAuthHeaders } from './auth'

export interface FetchLineageParams {
  familyId: string
}

export interface SetProgenitorParams {
  familyId: string
  personId: string | null
}

export class LineageApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'LineageApiError'
    this.status = status
    this.code = code
  }
}

function lineageApiBase(): string {
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return base ? base.replace(/\/$/, '') : ''
}

function isUsingRealApi(): boolean {
  const useFlag = import.meta.env.VITE_USE_GRAPH_API === 'true'
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return useFlag || Boolean(base)
}

/**
 * Fetch lineage projection for a family.
 *
 * @param params.familyId - Required family ID
 * @returns LineageResponse with progenitor_person_id and generations (snake_case)
 * @throws LineageApiError on API errors (401, 404, etc.)
 */
export async function fetchLineage(
  params: FetchLineageParams,
): Promise<LineageResponse> {
  const { familyId } = params

  if (!isUsingRealApi()) {
    throw new LineageApiError(
      '世系视图需要连接真实 API（当前为 mock 模式）',
      0,
    )
  }

  if (!familyId) {
    throw new LineageApiError('familyId is required', 400)
  }

  const base = lineageApiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/lineage`

  const res = await fetch(url, { headers: getAuthHeaders() })

  if (!res.ok) {
    let errorMsg = '加载世系视图失败'
    try {
      const errBody = await res.json()
      if (errBody.error) {
        if (errBody.error === 'authentication required') {
          errorMsg = '请先登录'
        } else if (errBody.error === 'not found') {
          errorMsg = '未找到家族'
        } else {
          errorMsg = errBody.error
        }
      }
    } catch {
      // ignore JSON parse errors
    }
    throw new LineageApiError(errorMsg, res.status)
  }

  return (await res.json()) as LineageResponse
}

/**
 * Set or clear the progenitor for a family.
 * Admin only - returns 403 for non-admin users.
 *
 * @param params.familyId - Required family ID
 * @param params.personId - Person ID to set as progenitor, or null to clear
 * @returns SetProgenitorResponse with progenitor_person_id
 * @throws LineageApiError on API errors (400, 403, 404, etc.)
 */
export async function setProgenitor(
  params: SetProgenitorParams,
): Promise<SetProgenitorResponse> {
  const { familyId, personId } = params

  if (!isUsingRealApi()) {
    throw new LineageApiError(
      '设置始迁祖需要连接真实 API（当前为 mock 模式）',
      0,
    )
  }

  if (!familyId) {
    throw new LineageApiError('familyId is required', 400)
  }

  const base = lineageApiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/progenitor`

  const body: SetProgenitorRequest = { person_id: personId }

  const res = await fetch(url, {
    method: 'PUT',
    headers: {
      ...getAuthHeaders(),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    let errorMsg = '设置始迁祖失败'
    let code: string | undefined
    try {
      const errBody = await res.json()
      if (errBody.error) {
        if (errBody.error === 'authentication required') {
          errorMsg = '请先登录'
        } else if (errBody.error === 'write access required') {
          errorMsg = '没有编辑权限'
          code = 'WRITE_ACCESS_REQUIRED'
        } else if (errBody.error === 'admin access required') {
          errorMsg = '需要管理员权限'
          code = 'ADMIN_REQUIRED'
        } else if (errBody.error === 'cannot set hidden person as progenitor') {
          errorMsg = '不能将已隐藏的成员设为始迁祖'
          code = 'HIDDEN_PERSON'
        } else if (errBody.error === 'person not in family') {
          errorMsg = '该成员不属于此家族'
          code = 'PERSON_NOT_IN_FAMILY'
        } else if (errBody.error === 'person not found') {
          errorMsg = '未找到该成员'
          code = 'PERSON_NOT_FOUND'
        } else {
          errorMsg = errBody.error
        }
      }
    } catch {
      // ignore JSON parse errors
    }
    throw new LineageApiError(errorMsg, res.status, code)
  }

  return (await res.json()) as SetProgenitorResponse
}

/**
 * Lineage generation index label for UI display.
 * Index starts at 1 (始祖 = 第1世).
 *
 * - 1 → "第1世（始祖）"
 * - 2 → "第2世"
 * - 3 → "第3世"
 * etc.
 */
export function getLineageLabel(index: number): string {
  if (index === 1) {
    return '第1世（始祖）'
  }
  return `第${index}世`
}
