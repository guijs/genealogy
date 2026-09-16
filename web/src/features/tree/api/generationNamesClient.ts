/**
 * P1 Generation Names (字辈) API client.
 *
 * GET /api/v1/families/{familyId}/generation-names
 * PUT /api/v1/families/{familyId}/generation-names
 *
 * Bearer auth required.
 *
 * Returns family's generation names (字辈诗) configuration:
 * - generation_names: ordered list of generation name characters/strings
 * - generation_name_align: 'A' or 'B' alignment mode
 *
 * Alignment modes:
 * - A (default): name index k (1-based) ↔ lineage generation k+1
 *   (第1世始祖无字辈；第2世用第1个字…)
 * - B: name index k ↔ lineage generation k
 *   (第1世用第1个字)
 */
import type { GenerationNamesRequest, GenerationNamesResponse, GenerationNameAlign } from './types'
import { getAuthHeaders } from './auth'

export interface FetchGenerationNamesParams {
  familyId: string
}

export interface UpdateGenerationNamesParams {
  familyId: string
  generationNames: string[]
  generationNameAlign?: GenerationNameAlign
}

export class GenerationNamesApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'GenerationNamesApiError'
    this.status = status
    this.code = code
  }
}

function generationNamesApiBase(): string {
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return base ? base.replace(/\/$/, '') : ''
}

function isUsingRealApi(): boolean {
  const useFlag = import.meta.env.VITE_USE_GRAPH_API === 'true'
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return useFlag || Boolean(base)
}

/**
 * Fetch generation names configuration for a family.
 *
 * @param params.familyId - Required family ID
 * @returns GenerationNamesResponse with generation_names and generation_name_align
 * @throws GenerationNamesApiError on API errors (401, 404, etc.)
 */
export async function fetchGenerationNames(
  params: FetchGenerationNamesParams,
): Promise<GenerationNamesResponse> {
  const { familyId } = params

  if (!isUsingRealApi()) {
    throw new GenerationNamesApiError(
      '字辈设置需要连接真实 API（当前为 mock 模式）',
      0,
    )
  }

  if (!familyId) {
    throw new GenerationNamesApiError('familyId is required', 400)
  }

  const base = generationNamesApiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/generation-names`

  const res = await fetch(url, { headers: getAuthHeaders() })

  if (!res.ok) {
    let errorMsg = '加载字辈设置失败'
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
    throw new GenerationNamesApiError(errorMsg, res.status)
  }

  return (await res.json()) as GenerationNamesResponse
}

/**
 * Update generation names configuration for a family.
 * Admin only - returns 403 for non-admin users.
 *
 * @param params.familyId - Required family ID
 * @param params.generationNames - Ordered list of generation name characters/strings
 * @param params.generationNameAlign - Optional alignment mode (A or B)
 * @returns GenerationNamesResponse with updated configuration
 * @throws GenerationNamesApiError on API errors (400, 401, 403, 404, etc.)
 */
export async function updateGenerationNames(
  params: UpdateGenerationNamesParams,
): Promise<GenerationNamesResponse> {
  const { familyId, generationNames, generationNameAlign } = params

  if (!isUsingRealApi()) {
    throw new GenerationNamesApiError(
      '更新字辈设置需要连接真实 API（当前为 mock 模式）',
      0,
    )
  }

  if (!familyId) {
    throw new GenerationNamesApiError('familyId is required', 400)
  }

  const base = generationNamesApiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/generation-names`

  const body: GenerationNamesRequest = {
    generation_names: generationNames,
  }
  if (generationNameAlign !== undefined) {
    body.generation_name_align = generationNameAlign
  }

  const res = await fetch(url, {
    method: 'PUT',
    headers: {
      ...getAuthHeaders(),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    let errorMsg = '更新字辈设置失败'
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
        } else if (errBody.error === 'not found') {
          errorMsg = '未找到家族'
          code = 'NOT_FOUND'
        } else if (errBody.error.includes('too many') || errBody.error.includes('超过')) {
          errorMsg = '字辈数量超过上限（最多200个）'
          code = 'TOO_MANY_ENTRIES'
        } else if (errBody.error.includes('too long') || errBody.error.includes('过长')) {
          errorMsg = '单个字辈字符过长（最多16字符）'
          code = 'ENTRY_TOO_LONG'
        } else {
          errorMsg = errBody.error
        }
      }
    } catch {
      // ignore JSON parse errors
    }
    throw new GenerationNamesApiError(errorMsg, res.status, code)
  }

  return (await res.json()) as GenerationNamesResponse
}

/**
 * Client-side validation for generation names.
 * Backend limits: max 200 entries, each ≤16 chars.
 *
 * @returns null if valid, otherwise error message
 */
export function validateGenerationNames(names: string[]): string | null {
  if (names.length > 200) {
    return '字辈数量超过上限（最多200个）'
  }
  for (let i = 0; i < names.length; i++) {
    if (names[i].length > 16) {
      return `第${i + 1}个字辈过长（最多16字符）`
    }
  }
  return null
}
