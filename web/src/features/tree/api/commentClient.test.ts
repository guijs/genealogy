import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  listComments,
  getComment,
  createComment,
  updateComment,
  deleteComment,
  CommentApiError,
  COMMENT_BODY_MAX_LENGTH,
} from './commentClient'
import type { CommentResponse, MentionRequest, MentionResponse, PersonRefRequest, PersonRefResponse } from './types'

const mockLocalStorage = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
  }
})()

const mockComment: CommentResponse = {
  id: 'comment-1',
  story_id: 'story-1',
  author_user_id: 'user-1',
  body: 'This is a test comment.',
  created_at: '2024-01-01T10:00:00Z',
  updated_at: '2024-01-01T10:00:00Z',
}

const mockMentions: MentionResponse[] = [
  { user_id: 'user-2', display_name_snapshot: '成员 user-2（编辑）', status: 'active' },
  { user_id: null, display_name_snapshot: '成员 user-3（已离开）', status: 'left' },
]

const mockCommentWithMentions: CommentResponse = {
  ...mockComment,
  mentions: mockMentions,
}

describe('commentClient', () => {
  describe('CommentApiError', () => {
    it('creates error with message and status', () => {
      const error = new CommentApiError('test error', 404)
      expect(error.message).toBe('test error')
      expect(error.status).toBe(404)
      expect(error.name).toBe('CommentApiError')
    })

    it('creates error with code', () => {
      const error = new CommentApiError('version conflict', 409, 'VERSION_CONFLICT')
      expect(error.code).toBe('VERSION_CONFLICT')
    })

    it('creates version conflict error with conflictComment', () => {
      const error = CommentApiError.versionConflict(mockComment)
      expect(error.status).toBe(409)
      expect(error.code).toBe('VERSION_CONFLICT')
      expect(error.conflictComment).toEqual(mockComment)
      expect(error.message).toBe('评论已被更新，已加载最新内容')
    })

    it('creates mock mode error', () => {
      const error = CommentApiError.mockMode()
      expect(error.status).toBe(0)
      expect(error.code).toBe('MOCK_MODE')
    })

    it('creates body too long error', () => {
      const error = CommentApiError.bodyTooLong()
      expect(error.code).toBe('BODY_TOO_LONG')
      expect(error.message).toContain(String(COMMENT_BODY_MAX_LENGTH))
    })

    it('creates body required error', () => {
      const error = CommentApiError.bodyRequired()
      expect(error.code).toBe('BODY_REQUIRED')
    })

    it('creates updated_at required error', () => {
      const error = CommentApiError.updatedAtRequired()
      expect(error.code).toBe('UPDATED_AT_REQUIRED')
    })

    it('creates invalid mention error with Chinese message', () => {
      const error = CommentApiError.invalidMention()
      expect(error.status).toBe(400)
      expect(error.code).toBe('INVALID_MENTION')
      expect(error.message).toBe('提及的成员无效或不是当前家族成员')
    })

    it('creates invalid person ref error with Chinese message', () => {
      const error = CommentApiError.invalidPersonRef()
      expect(error.status).toBe(400)
      expect(error.code).toBe('INVALID_PERSON_REF')
      expect(error.message).toBe('引用的人物无效或不存在于当前家族')
    })
  })

  describe('listComments', () => {
    const originalEnv = { ...import.meta.env }

    beforeEach(() => {
      vi.stubGlobal('fetch', vi.fn())
      vi.stubGlobal('localStorage', mockLocalStorage)
      mockLocalStorage.clear()
    })

    afterEach(() => {
      vi.unstubAllGlobals()
      Object.assign(import.meta.env, originalEnv)
    })

    it('throws error when not using real API', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'false'
      import.meta.env.VITE_GRAPH_API_BASE = ''
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(listComments({ familyId: 'family-1', storyId: 'story-1' })).rejects.toThrow(CommentApiError)
    })

    it('throws error when familyId is empty', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(listComments({ familyId: '', storyId: 'story-1' })).rejects.toThrow(CommentApiError)
    })

    it('throws error when storyId is empty', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(listComments({ familyId: 'family-1', storyId: '' })).rejects.toThrow(CommentApiError)
    })

    it('makes correct API call', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = { comments: [mockComment] }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await listComments({ familyId: 'family-1', storyId: 'story-1' })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories/story-1/comments',
        expect.objectContaining({
          method: 'GET',
          headers: expect.any(Object),
        }),
      )
      expect(result.comments).toHaveLength(1)
      expect(result.comments[0]).toEqual(mockComment)
    })

    it('handles 401 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 401,
      } as Response)

      await expect(listComments({ familyId: 'family-1', storyId: 'story-1' })).rejects.toThrow('请先登录')
    })

    it('handles 404 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
      } as Response)

      await expect(listComments({ familyId: 'family-1', storyId: 'story-1' })).rejects.toThrow('故事不存在')
    })
  })

  describe('createComment', () => {
    const originalEnv = { ...import.meta.env }

    beforeEach(() => {
      vi.stubGlobal('fetch', vi.fn())
      vi.stubGlobal('localStorage', mockLocalStorage)
      mockLocalStorage.clear()
    })

    afterEach(() => {
      vi.unstubAllGlobals()
      Object.assign(import.meta.env, originalEnv)
    })

    it('makes correct API call', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockComment),
      } as Response)

      const result = await createComment('family-1', 'story-1', {
        body: 'This is a test comment.',
      })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories/story-1/comments',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
          body: expect.any(String),
        }),
      )
      expect(result).toEqual(mockComment)
    })

    it('validates body is required', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(
        createComment('family-1', 'story-1', { body: '' }),
      ).rejects.toThrow(CommentApiError)
    })

    it('validates body is not just whitespace', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(
        createComment('family-1', 'story-1', { body: '   ' }),
      ).rejects.toThrow(CommentApiError)
    })

    it('validates body max length', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const longBody = 'x'.repeat(COMMENT_BODY_MAX_LENGTH + 1)

      await expect(
        createComment('family-1', 'story-1', { body: longBody }),
      ).rejects.toThrow(CommentApiError)
    })

    it('handles 403 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 403,
      } as Response)

      await expect(
        createComment('family-1', 'story-1', { body: 'Test body' }),
      ).rejects.toThrow('没有编辑权限')
    })

    it('sends mentions in request body', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockCommentWithMentions),
      } as Response)

      const mentions: MentionRequest[] = [
        { user_id: 'user-2', display_name_snapshot: '成员 user-2（编辑）' },
      ]

      const result = await createComment('family-1', 'story-1', {
        body: 'Hello @成员 user-2（编辑）',
        mentions,
      })

      expect(fetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('"mentions"'),
        }),
      )
      expect(result.mentions).toBeDefined()
      expect(result.mentions?.length).toBe(2)
    })

    it('handles 400 error with invalid mention', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
      } as Response)

      const invalidMentions: MentionRequest[] = [
        { user_id: 'non-existent-user', display_name_snapshot: 'Unknown' },
      ]

      try {
        await createComment('family-1', 'story-1', {
          body: 'Hello @Unknown',
          mentions: invalidMentions,
        })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(CommentApiError)
        const err = e as CommentApiError
        expect(err.status).toBe(400)
        expect(err.code).toBe('INVALID_MENTION')
        expect(err.message).toBe('提及的成员无效或不是当前家族成员')
      }
    })

    it('sends person_refs in request body', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const personRefs: PersonRefRequest[] = [
        { person_id: 'person-1', display_name_snapshot: '张三' },
      ]

      const commentWithRefs: CommentResponse = {
        ...mockComment,
        person_refs: [
          { person_id: 'person-1', display_name_snapshot: '张三', status: 'active', clickable: true },
        ],
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(commentWithRefs),
      } as Response)

      const result = await createComment('family-1', 'story-1', {
        body: 'Comment with #张三',
        person_refs: personRefs,
      })

      expect(fetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('"person_refs"'),
        }),
      )
      expect(result.person_refs).toBeDefined()
      expect(result.person_refs?.length).toBe(1)
    })

    it('handles 400 error with invalid person_refs', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
      } as Response)

      const invalidRefs: PersonRefRequest[] = [
        { person_id: 'non-existent', display_name_snapshot: 'Unknown' },
      ]

      try {
        await createComment('family-1', 'story-1', {
          body: 'Comment with #Unknown',
          person_refs: invalidRefs,
        })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(CommentApiError)
        const err = e as CommentApiError
        expect(err.status).toBe(400)
        expect(err.code).toBe('INVALID_PERSON_REF')
        expect(err.message).toBe('引用的人物无效或不存在于当前家族')
      }
    })

    it('creates comment without person_refs (omit)', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockComment),
      } as Response)

      await createComment('family-1', 'story-1', {
        body: 'Comment without refs',
      })

      const callBody = JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string)
      expect(callBody.person_refs).toBeUndefined()
    })
  })

  describe('updateComment', () => {
    const originalEnv = { ...import.meta.env }

    beforeEach(() => {
      vi.stubGlobal('fetch', vi.fn())
      vi.stubGlobal('localStorage', mockLocalStorage)
      mockLocalStorage.clear()
    })

    afterEach(() => {
      vi.unstubAllGlobals()
      Object.assign(import.meta.env, originalEnv)
    })

    it('makes correct API call with updated_at', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const updatedComment = { ...mockComment, updated_at: '2024-01-02T10:00:00Z', body: 'Updated body' }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(updatedComment),
      } as Response)

      const result = await updateComment('family-1', 'story-1', 'comment-1', {
        body: 'Updated body',
        updated_at: '2024-01-01T10:00:00Z',
      })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories/story-1/comments/comment-1',
        expect.objectContaining({
          method: 'PUT',
          body: expect.stringContaining('"updated_at":"2024-01-01T10:00:00Z"'),
        }),
      )
      expect(result.body).toBe('Updated body')
    })

    it('throws error when updated_at is not provided', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(
        updateComment('family-1', 'story-1', 'comment-1', {
          body: 'Updated body',
          updated_at: '',
        }),
      ).rejects.toThrow(CommentApiError)
    })

    it('throws error when updated_at is undefined', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(
        updateComment('family-1', 'story-1', 'comment-1', {
          body: 'Updated body',
          updated_at: undefined as unknown as string,
        }),
      ).rejects.toThrow(CommentApiError)

      try {
        await updateComment('family-1', 'story-1', 'comment-1', {
          body: 'Updated body',
          updated_at: undefined as unknown as string,
        })
      } catch (e) {
        expect(e).toBeInstanceOf(CommentApiError)
        expect((e as CommentApiError).code).toBe('UPDATED_AT_REQUIRED')
      }
    })

    it('handles 409 version conflict and parses CommentResponse', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const currentComment = { ...mockComment, updated_at: '2024-01-02T10:00:00Z', body: 'Someone else updated this' }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 409,
        json: () => Promise.resolve(currentComment),
      } as Response)

      try {
        await updateComment('family-1', 'story-1', 'comment-1', {
          body: 'My update',
          updated_at: '2024-01-01T10:00:00Z',
        })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(CommentApiError)
        const err = e as CommentApiError
        expect(err.status).toBe(409)
        expect(err.code).toBe('VERSION_CONFLICT')
        expect(err.conflictComment).toBeDefined()
        expect(err.conflictComment!.updated_at).toBe('2024-01-02T10:00:00Z')
        expect(err.conflictComment!.body).toBe('Someone else updated this')
        expect(err.message).toBe('评论已被更新，已加载最新内容')
      }
    })

    it('validates body max length on update', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const longBody = 'x'.repeat(COMMENT_BODY_MAX_LENGTH + 1)

      await expect(
        updateComment('family-1', 'story-1', 'comment-1', {
          body: longBody,
          updated_at: '2024-01-01T10:00:00Z',
        }),
      ).rejects.toThrow(CommentApiError)
    })

    it('sends mentions in update request body', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockCommentWithMentions),
      } as Response)

      const mentions: MentionRequest[] = [
        { user_id: 'user-2', display_name_snapshot: '成员 user-2（编辑）' },
      ]

      const result = await updateComment('family-1', 'story-1', 'comment-1', {
        body: 'Updated with @成员 user-2（编辑）',
        updated_at: '2024-01-01T10:00:00Z',
        mentions,
      })

      expect(fetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          method: 'PUT',
          body: expect.stringContaining('"mentions"'),
        }),
      )
      expect(result.mentions).toBeDefined()
    })

    it('handles 400 error with invalid mention on update', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
      } as Response)

      const invalidMentions: MentionRequest[] = [
        { user_id: 'non-existent-user', display_name_snapshot: 'Unknown' },
      ]

      try {
        await updateComment('family-1', 'story-1', 'comment-1', {
          body: 'Update with @Unknown',
          updated_at: '2024-01-01T10:00:00Z',
          mentions: invalidMentions,
        })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(CommentApiError)
        const err = e as CommentApiError
        expect(err.status).toBe(400)
        expect(err.code).toBe('INVALID_MENTION')
        expect(err.message).toBe('提及的成员无效或不是当前家族成员')
      }
    })

    it('sends person_refs in update request when provided', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const personRefs: PersonRefRequest[] = [
        { person_id: 'person-1', display_name_snapshot: '张三' },
      ]

      const commentWithRefs: CommentResponse = {
        ...mockComment,
        updated_at: '2024-01-02T10:00:00Z',
        person_refs: [
          { person_id: 'person-1', display_name_snapshot: '张三', status: 'active', clickable: true },
        ],
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(commentWithRefs),
      } as Response)

      const result = await updateComment('family-1', 'story-1', 'comment-1', {
        body: 'Updated with #张三',
        updated_at: '2024-01-01T10:00:00Z',
        person_refs: personRefs,
      })

      expect(fetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          method: 'PUT',
          body: expect.stringContaining('"person_refs"'),
        }),
      )
      expect(result.person_refs).toBeDefined()
    })

    it('clears person_refs when empty array provided', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const commentWithoutRefs: CommentResponse = {
        ...mockComment,
        updated_at: '2024-01-02T10:00:00Z',
        person_refs: [],
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(commentWithoutRefs),
      } as Response)

      await updateComment('family-1', 'story-1', 'comment-1', {
        body: 'Clear refs',
        updated_at: '2024-01-01T10:00:00Z',
        person_refs: [],
      })

      const callBody = JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string)
      expect(callBody.person_refs).toEqual([])
    })

    it('omits person_refs field when undefined (no change)', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ ...mockComment, updated_at: '2024-01-02T10:00:00Z' }),
      } as Response)

      await updateComment('family-1', 'story-1', 'comment-1', {
        body: 'Update without touching refs',
        updated_at: '2024-01-01T10:00:00Z',
      })

      const callBody = JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string)
      expect(callBody.person_refs).toBeUndefined()
    })

    it('handles 400 error with invalid person_refs on update', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
      } as Response)

      const invalidRefs: PersonRefRequest[] = [
        { person_id: 'deleted-person', display_name_snapshot: 'Deleted' },
      ]

      try {
        await updateComment('family-1', 'story-1', 'comment-1', {
          body: 'Update with #Deleted',
          updated_at: '2024-01-01T10:00:00Z',
          person_refs: invalidRefs,
        })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(CommentApiError)
        const err = e as CommentApiError
        expect(err.status).toBe(400)
        expect(err.code).toBe('INVALID_PERSON_REF')
      }
    })
  })

  describe('deleteComment', () => {
    const originalEnv = { ...import.meta.env }

    beforeEach(() => {
      vi.stubGlobal('fetch', vi.fn())
      vi.stubGlobal('localStorage', mockLocalStorage)
      mockLocalStorage.clear()
    })

    afterEach(() => {
      vi.unstubAllGlobals()
      Object.assign(import.meta.env, originalEnv)
    })

    it('makes correct API call', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
      } as Response)

      await deleteComment('family-1', 'story-1', 'comment-1')

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories/story-1/comments/comment-1',
        expect.objectContaining({
          method: 'DELETE',
        }),
      )
    })

    it('handles 403 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 403,
      } as Response)

      await expect(
        deleteComment('family-1', 'story-1', 'comment-1'),
      ).rejects.toThrow('没有编辑权限')
    })

    it('handles 404 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
      } as Response)

      await expect(
        deleteComment('family-1', 'story-1', 'comment-1'),
      ).rejects.toThrow('评论不存在')
    })
  })

  describe('getComment', () => {
    const originalEnv = { ...import.meta.env }

    beforeEach(() => {
      vi.stubGlobal('fetch', vi.fn())
      vi.stubGlobal('localStorage', mockLocalStorage)
      mockLocalStorage.clear()
    })

    afterEach(() => {
      vi.unstubAllGlobals()
      Object.assign(import.meta.env, originalEnv)
    })

    it('makes correct API call', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockComment),
      } as Response)

      const result = await getComment('family-1', 'story-1', 'comment-1')

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories/story-1/comments/comment-1',
        expect.objectContaining({
          method: 'GET',
        }),
      )
      expect(result).toEqual(mockComment)
    })

    it('handles 404 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
      } as Response)

      await expect(getComment('family-1', 'story-1', 'comment-1')).rejects.toThrow('评论不存在')
    })
  })
})
