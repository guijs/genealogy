/**
 * Family Story API client
 *
 * - GET /api/v1/families/{familyId}/stories — list stories (optional ?personId=)
 * - GET /api/v1/families/{familyId}/stories/{storyId} — get story detail
 * - POST /api/v1/families/{familyId}/stories — create story
 * - PUT /api/v1/families/{familyId}/stories/{storyId} — update story (version required)
 * - DELETE /api/v1/families/{familyId}/stories/{storyId} — delete story (version required)
 *
 * Constraints:
 * - Auth: Authorization: Bearer <token>
 * - familyId in path
 * - Only available when real API mode is on (VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE)
 * - 409 VersionConflict on PUT/DELETE returns full StoryResponse (not ErrorResponse)
 * - body required for create, ≤10000 chars
 * - version required for PUT/DELETE
 */
import { isUsingGraphApi } from './graphClient'
import { getAuthHeadersWithContentType, AuthRequiredError } from './auth'
import type {
  StoryResponse,
  CreateStoryRequest,
  UpdateStoryRequest,
  DeleteStoryRequest,
  StoriesListResponse,
} from './types'

export const STORY_BODY_MAX_LENGTH = 10000

export class StoryApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly conflictStory?: StoryResponse

  constructor(
    message: string,
    status: number,
    code?: string,
    conflictStory?: StoryResponse,
  ) {
    super(message)
    this.name = 'StoryApiError'
    this.status = status
    this.code = code
    this.conflictStory = conflictStory
  }

  static fromStatus(status: number, context?: 'list' | 'detail' | 'create' | 'update' | 'delete'): StoryApiError {
    const messages: Record<number, string> = {
      400: '请求参数无效',
      401: '请先登录',
      403: '没有编辑权限',
      404: context === 'list' ? '未找到家族' : '故事不存在',
    }
    return new StoryApiError(
      messages[status] ?? `请求失败: ${status}`,
      status,
    )
  }

  static versionConflict(currentStory: StoryResponse): StoryApiError {
    return new StoryApiError(
      '内容已被他人更新，已加载最新版本',
      409,
      'VERSION_CONFLICT',
      currentStory,
    )
  }

  static mockMode(): StoryApiError {
    return new StoryApiError(
      '故事功能需要连接真实 API（当前为 mock 模式）',
      0,
      'MOCK_MODE',
    )
  }

  static bodyTooLong(): StoryApiError {
    return new StoryApiError(
      `故事内容不能超过 ${STORY_BODY_MAX_LENGTH} 字`,
      0,
      'BODY_TOO_LONG',
    )
  }

  static bodyRequired(): StoryApiError {
    return new StoryApiError(
      '故事内容不能为空',
      0,
      'BODY_REQUIRED',
    )
  }

  static invalidPersonRef(): StoryApiError {
    return new StoryApiError(
      '引用的人物无效或不存在于当前家族',
      400,
      'INVALID_PERSON_REF',
    )
  }

  static versionRequired(): StoryApiError {
    return new StoryApiError(
      '更新或删除故事需要提供版本号',
      0,
      'VERSION_REQUIRED',
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
      throw new StoryApiError('请先登录', 401, 'AUTH_MISSING')
    }
    throw err
  }
}

function ensureRealApi(): void {
  if (!isUsingGraphApi()) {
    throw StoryApiError.mockMode()
  }
}

function validateBody(body: string | undefined, forCreate: boolean): void {
  if (forCreate && (!body || body.trim().length === 0)) {
    throw StoryApiError.bodyRequired()
  }
  if (body && body.length > STORY_BODY_MAX_LENGTH) {
    throw StoryApiError.bodyTooLong()
  }
}

function validateVersion(version: number | undefined): void {
  if (version === undefined || version === null) {
    throw StoryApiError.versionRequired()
  }
}

export interface ListStoriesParams {
  familyId: string
  personId?: string
}

/**
 * List stories for a family.
 * GET /api/v1/families/{familyId}/stories?personId={personId}
 *
 * @param params.familyId - The family ID (required)
 * @param params.personId - Optional person ID to filter stories linked to this person
 * @returns StoriesListResponse { stories: StoryResponse[] }
 * @throws StoryApiError
 */
export async function listStories(params: ListStoriesParams): Promise<StoriesListResponse> {
  ensureRealApi()

  const { familyId, personId } = params

  if (!familyId) {
    throw new StoryApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }

  const base = apiBase()
  let url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories`
  if (personId) {
    url += `?personId=${encodeURIComponent(personId)}`
  }

  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw StoryApiError.fromStatus(res.status, 'list')
  }

  return (await res.json()) as StoriesListResponse
}

/**
 * Get a single story by ID.
 * GET /api/v1/families/{familyId}/stories/{storyId}
 *
 * @param familyId - The family ID
 * @param storyId - The story ID
 * @returns StoryResponse
 * @throws StoryApiError
 */
export async function getStory(familyId: string, storyId: string): Promise<StoryResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new StoryApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!storyId) {
    throw new StoryApiError('storyId 不能为空', 400, 'INVALID_STORY_ID')
  }

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories/${encodeURIComponent(storyId)}`

  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw StoryApiError.fromStatus(res.status, 'detail')
  }

  return (await res.json()) as StoryResponse
}

/**
 * Create a new story.
 * POST /api/v1/families/{familyId}/stories
 *
 * @param familyId - The family ID
 * @param data - CreateStoryRequest { title?, body, narrative_time?, person_ids? }
 * @returns StoryResponse (201 Created)
 * @throws StoryApiError
 */
export async function createStory(
  familyId: string,
  data: CreateStoryRequest,
): Promise<StoryResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new StoryApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }

  validateBody(data.body, true)

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories`

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    if (res.status === 400 && data.person_refs && data.person_refs.length > 0) {
      throw StoryApiError.invalidPersonRef()
    }
    throw StoryApiError.fromStatus(res.status, 'create')
  }

  return (await res.json()) as StoryResponse
}

/**
 * Update an existing story.
 * PUT /api/v1/families/{familyId}/stories/{storyId}
 *
 * @param familyId - The family ID
 * @param storyId - The story ID
 * @param data - UpdateStoryRequest { title?, body?, narrative_time?, person_ids?, version }
 * @returns StoryResponse
 * @throws StoryApiError - On 409, conflictStory contains the current story from server
 */
export async function updateStory(
  familyId: string,
  storyId: string,
  data: UpdateStoryRequest,
): Promise<StoryResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new StoryApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!storyId) {
    throw new StoryApiError('storyId 不能为空', 400, 'INVALID_STORY_ID')
  }

  validateVersion(data.version)
  validateBody(data.body, false)

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories/${encodeURIComponent(storyId)}`

  const res = await fetch(url, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    if (res.status === 409) {
      const currentStory = (await res.json()) as StoryResponse
      throw StoryApiError.versionConflict(currentStory)
    }
    if (res.status === 400 && data.person_refs && data.person_refs.length > 0) {
      throw StoryApiError.invalidPersonRef()
    }
    throw StoryApiError.fromStatus(res.status, 'update')
  }

  return (await res.json()) as StoryResponse
}

/**
 * Delete a story.
 * DELETE /api/v1/families/{familyId}/stories/{storyId}
 * Body: { version: number }
 *
 * @param familyId - The family ID
 * @param storyId - The story ID
 * @param version - The current version (required)
 * @throws StoryApiError - On 409, conflictStory contains the current story from server
 */
export async function deleteStory(
  familyId: string,
  storyId: string,
  version: number,
): Promise<void> {
  ensureRealApi()

  if (!familyId) {
    throw new StoryApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!storyId) {
    throw new StoryApiError('storyId 不能为空', 400, 'INVALID_STORY_ID')
  }

  validateVersion(version)

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories/${encodeURIComponent(storyId)}`

  const body: DeleteStoryRequest = { version }

  const res = await fetch(url, {
    method: 'DELETE',
    headers: authHeaders(),
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    if (res.status === 409) {
      const currentStory = (await res.json()) as StoryResponse
      throw StoryApiError.versionConflict(currentStory)
    }
    throw StoryApiError.fromStatus(res.status, 'delete')
  }
}

/**
 * Format narrative_time for display.
 * Input: YYYY-MM-DD or YYYY-MM or YYYY
 * Output: Human readable Chinese format
 */
export function formatNarrativeTime(narrativeTime: string | null): string {
  if (!narrativeTime) return ''
  
  const parts = narrativeTime.split('-')
  if (parts.length === 3) {
    return `${parts[0]}年${parseInt(parts[1], 10)}月${parseInt(parts[2], 10)}日`
  } else if (parts.length === 2) {
    return `${parts[0]}年${parseInt(parts[1], 10)}月`
  } else if (parts.length === 1) {
    return `${parts[0]}年`
  }
  return narrativeTime
}
