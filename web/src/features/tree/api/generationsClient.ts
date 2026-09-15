/**
 * P1 Generations projection API client.
 *
 * GET /api/v1/families/{familyId}/generations?focusPersonId={uuid}
 * Bearer auth required.
 *
 * Returns ego-relative generation layers:
 * - Ego (focusPersonId) at index 0
 * - Ancestors at negative indices (-1, -2, ...)
 * - Descendants at positive indices (+1, +2, ...)
 *
 * Biological edges only; adoptive/dissolved edges excluded.
 * Hidden persons excluded.
 */
import type { GenerationsProjection } from './types'
import { getAuthHeaders } from './auth'

export interface FetchGenerationsParams {
  familyId: string
  focusPersonId?: string
}

export class GenerationsApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'GenerationsApiError'
    this.status = status
  }
}

function generationsApiBase(): string {
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return base ? base.replace(/\/$/, '') : ''
}

function isUsingRealApi(): boolean {
  const useFlag = import.meta.env.VITE_USE_GRAPH_API === 'true'
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return useFlag || Boolean(base)
}

/**
 * Fetch generations projection for a family.
 *
 * @param params.familyId - Required family ID
 * @param params.focusPersonId - Optional focus person; backend defaults to earliest person if omitted
 * @returns GenerationsProjection with sorted generations
 * @throws GenerationsApiError on API errors (401, 404, etc.)
 */
export async function fetchGenerations(
  params: FetchGenerationsParams,
): Promise<GenerationsProjection> {
  const { familyId, focusPersonId } = params

  if (!isUsingRealApi()) {
    throw new GenerationsApiError(
      '世代视图需要连接真实 API（当前为 mock 模式）',
      0,
    )
  }

  if (!familyId) {
    throw new GenerationsApiError('familyId is required', 400)
  }

  const base = generationsApiBase()
  const qs = new URLSearchParams()
  if (focusPersonId) {
    qs.set('focusPersonId', focusPersonId)
  }

  const queryString = qs.toString()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/generations${queryString ? `?${queryString}` : ''}`

  const res = await fetch(url, { headers: getAuthHeaders() })

  if (!res.ok) {
    let errorMsg = '加载世代视图失败'
    try {
      const errBody = await res.json()
      if (errBody.error) {
        if (errBody.error === 'authentication required') {
          errorMsg = '请先登录'
        } else if (errBody.error === 'not found') {
          errorMsg = '未找到家族或人物'
        } else if (errBody.error === 'no persons in family') {
          errorMsg = '家族暂无成员'
        } else if (errBody.error === 'invalid focusPersonId') {
          errorMsg = '无效的焦点人物ID'
        } else {
          errorMsg = errBody.error
        }
      }
    } catch {
      // ignore JSON parse errors
    }
    throw new GenerationsApiError(errorMsg, res.status)
  }

  return (await res.json()) as GenerationsProjection
}

/**
 * Generation index label for UI display.
 * - 0 → "本代 (0)"
 * - -1 → "上一代 (-1)"
 * - +1 → "下一代 (+1)"
 * etc.
 */
export function getGenerationLabel(index: number): string {
  if (index === 0) {
    return '本代 (0)'
  }
  if (index < 0) {
    const absIndex = Math.abs(index)
    if (absIndex === 1) {
      return '上一代 (-1)'
    }
    return `上${absIndex}代 (${index})`
  }
  if (index === 1) {
    return '下一代 (+1)'
  }
  return `下${index}代 (+${index})`
}
