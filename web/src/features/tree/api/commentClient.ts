/**
 * Story Comment API client
 *
 * - GET /api/v1/families/{familyId}/stories/{storyId}/comments — list comments (flat, single layer)
 * - GET /api/v1/families/{familyId}/stories/{storyId}/comments/{id} — get comment detail (optional)
 * - POST /api/v1/families/{familyId}/stories/{storyId}/comments — create comment
 * - PUT /api/v1/families/{familyId}/stories/{storyId}/comments/{id} — update comment (updated_at required)
 * - DELETE /api/v1/families/{familyId}/stories/{storyId}/comments/{id} — delete comment
 *
 * Constraints:
 * - Auth: Authorization: Bearer <token>
 * - familyId, storyId in path
 * - Only available when real API mode is on (VITE_USE_GRAPH_API / VITE_GRAPH_API_BASE)
 * - 409 VersionConflict on PUT returns full CommentResponse (optimistic concurrency via updated_at)
 * - body required, blank rejected, ≤1000 chars
 * - updated_at (ISO-8601 Instant) required for PUT
 *
 * Access (v0.2):
 * - Read: admin, editor, viewer
 * - Create: admin, editor only (viewer 403)
 * - Edit own: admin, editor (author only)
 * - Delete own: admin, editor
 * - Delete others: admin only (editor 403)
 */
import { isUsingGraphApi } from './graphClient'
import { getAuthHeadersWithContentType, AuthRequiredError } from './auth'
import type {
  CommentResponse,
  CreateCommentRequest,
  UpdateCommentRequest,
  CommentsListResponse,
} from './types'

export const COMMENT_BODY_MAX_LENGTH = 1000

export class CommentApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly conflictComment?: CommentResponse

  constructor(
    message: string,
    status: number,
    code?: string,
    conflictComment?: CommentResponse,
  ) {
    super(message)
    this.name = 'CommentApiError'
    this.status = status
    this.code = code
    this.conflictComment = conflictComment
  }

  static fromStatus(status: number, context?: 'list' | 'detail' | 'create' | 'update' | 'delete'): CommentApiError {
    const messages: Record<number, string> = {
      400: '请求参数无效',
      401: '请先登录',
      403: '没有编辑权限',
      404: context === 'list' ? '故事不存在' : '评论不存在',
    }
    return new CommentApiError(
      messages[status] ?? `请求失败: ${status}`,
      status,
    )
  }

  static invalidMention(): CommentApiError {
    return new CommentApiError(
      '提及的成员无效或不是当前家族成员',
      400,
      'INVALID_MENTION',
    )
  }

  static invalidPersonRef(): CommentApiError {
    return new CommentApiError(
      '引用的人物无效或不存在于当前家族',
      400,
      'INVALID_PERSON_REF',
    )
  }

  static versionConflict(currentComment: CommentResponse): CommentApiError {
    return new CommentApiError(
      '评论已被更新，已加载最新内容',
      409,
      'VERSION_CONFLICT',
      currentComment,
    )
  }

  static mockMode(): CommentApiError {
    return new CommentApiError(
      '评论功能需要连接真实 API（当前为 mock 模式）',
      0,
      'MOCK_MODE',
    )
  }

  static bodyTooLong(): CommentApiError {
    return new CommentApiError(
      `评论内容不能超过 ${COMMENT_BODY_MAX_LENGTH} 字`,
      0,
      'BODY_TOO_LONG',
    )
  }

  static bodyRequired(): CommentApiError {
    return new CommentApiError(
      '评论内容不能为空',
      0,
      'BODY_REQUIRED',
    )
  }

  static updatedAtRequired(): CommentApiError {
    return new CommentApiError(
      '更新评论需要提供 updated_at',
      0,
      'UPDATED_AT_REQUIRED',
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
      throw new CommentApiError('请先登录', 401, 'AUTH_MISSING')
    }
    throw err
  }
}

function ensureRealApi(): void {
  if (!isUsingGraphApi()) {
    throw CommentApiError.mockMode()
  }
}

function validateBody(body: string | undefined, forCreate: boolean): void {
  if (forCreate && (!body || body.trim().length === 0)) {
    throw CommentApiError.bodyRequired()
  }
  if (body && body.length > COMMENT_BODY_MAX_LENGTH) {
    throw CommentApiError.bodyTooLong()
  }
}

function validateUpdatedAt(updatedAt: string | undefined): void {
  if (!updatedAt) {
    throw CommentApiError.updatedAtRequired()
  }
}

export interface ListCommentsParams {
  familyId: string
  storyId: string
}

/**
 * List comments for a story.
 * GET /api/v1/families/{familyId}/stories/{storyId}/comments
 *
 * @param params.familyId - The family ID (required)
 * @param params.storyId - The story ID (required)
 * @returns CommentsListResponse { comments: CommentResponse[] }
 * @throws CommentApiError
 */
export async function listComments(params: ListCommentsParams): Promise<CommentsListResponse> {
  ensureRealApi()

  const { familyId, storyId } = params

  if (!familyId) {
    throw new CommentApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!storyId) {
    throw new CommentApiError('storyId 不能为空', 400, 'INVALID_STORY_ID')
  }

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories/${encodeURIComponent(storyId)}/comments`

  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw CommentApiError.fromStatus(res.status, 'list')
  }

  return (await res.json()) as CommentsListResponse
}

/**
 * Get a single comment by ID.
 * GET /api/v1/families/{familyId}/stories/{storyId}/comments/{commentId}
 *
 * @param familyId - The family ID
 * @param storyId - The story ID
 * @param commentId - The comment ID
 * @returns CommentResponse
 * @throws CommentApiError
 */
export async function getComment(
  familyId: string,
  storyId: string,
  commentId: string,
): Promise<CommentResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new CommentApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!storyId) {
    throw new CommentApiError('storyId 不能为空', 400, 'INVALID_STORY_ID')
  }
  if (!commentId) {
    throw new CommentApiError('commentId 不能为空', 400, 'INVALID_COMMENT_ID')
  }

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories/${encodeURIComponent(storyId)}/comments/${encodeURIComponent(commentId)}`

  const res = await fetch(url, {
    method: 'GET',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw CommentApiError.fromStatus(res.status, 'detail')
  }

  return (await res.json()) as CommentResponse
}

/**
 * Create a new comment.
 * POST /api/v1/families/{familyId}/stories/{storyId}/comments
 *
 * @param familyId - The family ID
 * @param storyId - The story ID
 * @param data - CreateCommentRequest { body, mentions? }
 * @returns CommentResponse (201 Created)
 * @throws CommentApiError - 400 for invalid mentions (non-current-member user_id)
 */
export async function createComment(
  familyId: string,
  storyId: string,
  data: CreateCommentRequest,
): Promise<CommentResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new CommentApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!storyId) {
    throw new CommentApiError('storyId 不能为空', 400, 'INVALID_STORY_ID')
  }

  validateBody(data.body, true)

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories/${encodeURIComponent(storyId)}/comments`

  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    if (res.status === 400) {
      if (data.person_refs && data.person_refs.length > 0) {
        throw CommentApiError.invalidPersonRef()
      }
      if (data.mentions && data.mentions.length > 0) {
        throw CommentApiError.invalidMention()
      }
    }
    throw CommentApiError.fromStatus(res.status, 'create')
  }

  return (await res.json()) as CommentResponse
}

/**
 * Update an existing comment.
 * PUT /api/v1/families/{familyId}/stories/{storyId}/comments/{commentId}
 *
 * @param familyId - The family ID
 * @param storyId - The story ID
 * @param commentId - The comment ID
 * @param data - UpdateCommentRequest { body, updated_at, mentions? }
 * @returns CommentResponse
 * @throws CommentApiError - On 409, conflictComment contains the current comment from server
 *                          On 400 with mentions, throws INVALID_MENTION error
 */
export async function updateComment(
  familyId: string,
  storyId: string,
  commentId: string,
  data: UpdateCommentRequest,
): Promise<CommentResponse> {
  ensureRealApi()

  if (!familyId) {
    throw new CommentApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!storyId) {
    throw new CommentApiError('storyId 不能为空', 400, 'INVALID_STORY_ID')
  }
  if (!commentId) {
    throw new CommentApiError('commentId 不能为空', 400, 'INVALID_COMMENT_ID')
  }

  validateUpdatedAt(data.updated_at)
  validateBody(data.body, true)

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories/${encodeURIComponent(storyId)}/comments/${encodeURIComponent(commentId)}`

  const res = await fetch(url, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    if (res.status === 409) {
      const currentComment = (await res.json()) as CommentResponse
      throw CommentApiError.versionConflict(currentComment)
    }
    if (res.status === 400) {
      if (data.person_refs && data.person_refs.length > 0) {
        throw CommentApiError.invalidPersonRef()
      }
      if (data.mentions && data.mentions.length > 0) {
        throw CommentApiError.invalidMention()
      }
    }
    throw CommentApiError.fromStatus(res.status, 'update')
  }

  return (await res.json()) as CommentResponse
}

/**
 * Delete a comment.
 * DELETE /api/v1/families/{familyId}/stories/{storyId}/comments/{commentId}
 *
 * @param familyId - The family ID
 * @param storyId - The story ID
 * @param commentId - The comment ID
 * @throws CommentApiError
 */
export async function deleteComment(
  familyId: string,
  storyId: string,
  commentId: string,
): Promise<void> {
  ensureRealApi()

  if (!familyId) {
    throw new CommentApiError('familyId 不能为空', 400, 'INVALID_FAMILY_ID')
  }
  if (!storyId) {
    throw new CommentApiError('storyId 不能为空', 400, 'INVALID_STORY_ID')
  }
  if (!commentId) {
    throw new CommentApiError('commentId 不能为空', 400, 'INVALID_COMMENT_ID')
  }

  const base = apiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/stories/${encodeURIComponent(storyId)}/comments/${encodeURIComponent(commentId)}`

  const res = await fetch(url, {
    method: 'DELETE',
    headers: authHeaders(),
  })

  if (!res.ok) {
    throw CommentApiError.fromStatus(res.status, 'delete')
  }
}
