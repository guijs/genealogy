import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  listStories,
  getStory,
  createStory,
  updateStory,
  deleteStory,
  formatNarrativeTime,
  StoryApiError,
  STORY_BODY_MAX_LENGTH,
} from './storyClient'
import type { StoryResponse, PersonRefRequest, PersonRefResponse } from './types'

const mockLocalStorage = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
  }
})()

const mockStory: StoryResponse = {
  id: 'story-1',
  family_id: 'family-1',
  title: 'Test Story',
  body: 'This is a test story body.',
  person_ids: ['person-1', 'person-2'],
  narrative_time: '1990-05-15',
  created_by: 'user-1',
  updated_by: 'user-1',
  created_at: '2024-01-01T00:00:00Z',
  updated_at: '2024-01-02T00:00:00Z',
  version: 1,
}

describe('storyClient', () => {
  describe('formatNarrativeTime', () => {
    it('returns empty string for null', () => {
      expect(formatNarrativeTime(null)).toBe('')
    })

    it('formats full date YYYY-MM-DD', () => {
      expect(formatNarrativeTime('1990-05-15')).toBe('1990年5月15日')
    })

    it('formats partial date YYYY-MM', () => {
      expect(formatNarrativeTime('1990-05')).toBe('1990年5月')
    })

    it('formats year only YYYY', () => {
      expect(formatNarrativeTime('1990')).toBe('1990年')
    })

    it('treats single non-numeric string as year', () => {
      expect(formatNarrativeTime('invalid')).toBe('invalid年')
    })
  })

  describe('StoryApiError', () => {
    it('creates error with message and status', () => {
      const error = new StoryApiError('test error', 404)
      expect(error.message).toBe('test error')
      expect(error.status).toBe(404)
      expect(error.name).toBe('StoryApiError')
    })

    it('creates error with code', () => {
      const error = new StoryApiError('version conflict', 409, 'VERSION_CONFLICT')
      expect(error.code).toBe('VERSION_CONFLICT')
    })

    it('creates version conflict error with conflictStory', () => {
      const error = StoryApiError.versionConflict(mockStory)
      expect(error.status).toBe(409)
      expect(error.code).toBe('VERSION_CONFLICT')
      expect(error.conflictStory).toEqual(mockStory)
      expect(error.message).toBe('内容已被他人更新，已加载最新版本')
    })

    it('creates mock mode error', () => {
      const error = StoryApiError.mockMode()
      expect(error.status).toBe(0)
      expect(error.code).toBe('MOCK_MODE')
    })

    it('creates body too long error', () => {
      const error = StoryApiError.bodyTooLong()
      expect(error.code).toBe('BODY_TOO_LONG')
      expect(error.message).toContain(String(STORY_BODY_MAX_LENGTH))
    })

    it('creates body required error', () => {
      const error = StoryApiError.bodyRequired()
      expect(error.code).toBe('BODY_REQUIRED')
    })

    it('creates version required error', () => {
      const error = StoryApiError.versionRequired()
      expect(error.code).toBe('VERSION_REQUIRED')
    })

    it('creates invalid person ref error with Chinese message', () => {
      const error = StoryApiError.invalidPersonRef()
      expect(error.status).toBe(400)
      expect(error.code).toBe('INVALID_PERSON_REF')
      expect(error.message).toBe('引用的人物无效或不存在于当前家族')
    })
  })

  describe('listStories', () => {
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

      await expect(listStories({ familyId: 'family-1' })).rejects.toThrow(StoryApiError)
    })

    it('throws error when familyId is empty', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(listStories({ familyId: '' })).rejects.toThrow(StoryApiError)
    })

    it('makes correct API call without personId', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = { stories: [mockStory] }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await listStories({ familyId: 'family-1' })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories',
        expect.objectContaining({
          method: 'GET',
          headers: expect.any(Object),
        }),
      )
      expect(result.stories).toHaveLength(1)
      expect(result.stories[0]).toEqual(mockStory)
    })

    it('makes correct API call with personId filter', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = { stories: [mockStory] }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      await listStories({ familyId: 'family-1', personId: 'person-1' })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories?personId=person-1',
        expect.any(Object),
      )
    })

    it('handles 401 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 401,
      } as Response)

      await expect(listStories({ familyId: 'family-1' })).rejects.toThrow('请先登录')
    })

    it('handles 404 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
      } as Response)

      await expect(listStories({ familyId: 'family-1' })).rejects.toThrow('未找到家族')
    })
  })

  describe('createStory', () => {
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
        json: () => Promise.resolve(mockStory),
      } as Response)

      const result = await createStory('family-1', {
        title: 'Test Story',
        body: 'This is a test story body.',
        narrative_time: '1990-05-15',
        person_ids: ['person-1'],
      })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
          body: expect.any(String),
        }),
      )
      expect(result).toEqual(mockStory)
    })

    it('validates body is required', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(
        createStory('family-1', { body: '' }),
      ).rejects.toThrow(StoryApiError)
    })

    it('validates body max length', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const longBody = 'x'.repeat(STORY_BODY_MAX_LENGTH + 1)

      await expect(
        createStory('family-1', { body: longBody }),
      ).rejects.toThrow(StoryApiError)
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
        createStory('family-1', { body: 'Test body' }),
      ).rejects.toThrow('没有编辑权限')
    })

    it('sends person_refs in request body', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const personRefs: PersonRefRequest[] = [
        { person_id: 'person-1', display_name_snapshot: '张三' },
      ]

      const storyWithRefs: StoryResponse = {
        ...mockStory,
        person_refs: [
          { person_id: 'person-1', display_name_snapshot: '张三', status: 'active', clickable: true },
        ],
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(storyWithRefs),
      } as Response)

      const result = await createStory('family-1', {
        body: 'Story with person refs',
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
      expect(result.person_refs?.[0].person_id).toBe('person-1')
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
        await createStory('family-1', {
          body: 'Story with invalid refs',
          person_refs: invalidRefs,
        })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(StoryApiError)
        const err = e as StoryApiError
        expect(err.status).toBe(400)
        expect(err.code).toBe('INVALID_PERSON_REF')
        expect(err.message).toBe('引用的人物无效或不存在于当前家族')
      }
    })

    it('creates story without person_refs (omit)', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockStory),
      } as Response)

      await createStory('family-1', {
        body: 'Story without person refs',
      })

      const callBody = JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string)
      expect(callBody.person_refs).toBeUndefined()
    })
  })

  describe('updateStory', () => {
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

    it('makes correct API call with version', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const updatedStory = { ...mockStory, version: 2 }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(updatedStory),
      } as Response)

      const result = await updateStory('family-1', 'story-1', {
        body: 'Updated body',
        version: 1,
      })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories/story-1',
        expect.objectContaining({
          method: 'PUT',
          body: expect.stringContaining('"version":1'),
        }),
      )
      expect(result.version).toBe(2)
    })

    it('throws error when version is not provided', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(
        updateStory('family-1', 'story-1', {
          body: 'Updated body',
          version: undefined as unknown as number,
        }),
      ).rejects.toThrow(StoryApiError)
    })

    it('handles 409 version conflict and parses StoryResponse', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const currentStory = { ...mockStory, version: 2, body: 'Someone else updated this' }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 409,
        json: () => Promise.resolve(currentStory),
      } as Response)

      try {
        await updateStory('family-1', 'story-1', {
          body: 'My update',
          version: 1,
        })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(StoryApiError)
        const err = e as StoryApiError
        expect(err.status).toBe(409)
        expect(err.code).toBe('VERSION_CONFLICT')
        expect(err.conflictStory).toBeDefined()
        expect(err.conflictStory!.version).toBe(2)
        expect(err.conflictStory!.body).toBe('Someone else updated this')
        expect(err.message).toBe('内容已被他人更新，已加载最新版本')
      }
    })

    it('sends person_refs in update request when provided', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const personRefs: PersonRefRequest[] = [
        { person_id: 'person-1', display_name_snapshot: '张三' },
      ]

      const storyWithRefs: StoryResponse = {
        ...mockStory,
        version: 2,
        person_refs: [
          { person_id: 'person-1', display_name_snapshot: '张三', status: 'active', clickable: true },
        ],
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(storyWithRefs),
      } as Response)

      const result = await updateStory('family-1', 'story-1', {
        body: 'Updated with refs',
        version: 1,
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

      const storyWithoutRefs: StoryResponse = {
        ...mockStory,
        version: 2,
        person_refs: [],
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(storyWithoutRefs),
      } as Response)

      await updateStory('family-1', 'story-1', {
        body: 'Clear refs',
        version: 1,
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
        json: () => Promise.resolve({ ...mockStory, version: 2 }),
      } as Response)

      await updateStory('family-1', 'story-1', {
        body: 'Update without touching refs',
        version: 1,
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
        await updateStory('family-1', 'story-1', {
          body: 'Update with invalid refs',
          version: 1,
          person_refs: invalidRefs,
        })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(StoryApiError)
        const err = e as StoryApiError
        expect(err.status).toBe(400)
        expect(err.code).toBe('INVALID_PERSON_REF')
      }
    })
  })

  describe('deleteStory', () => {
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

    it('makes correct API call with version in body', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
      } as Response)

      await deleteStory('family-1', 'story-1', 1)

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories/story-1',
        expect.objectContaining({
          method: 'DELETE',
          body: JSON.stringify({ version: 1 }),
        }),
      )
    })

    it('throws error when version is not provided', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(
        deleteStory('family-1', 'story-1', undefined as unknown as number),
      ).rejects.toThrow(StoryApiError)
    })

    it('handles 409 version conflict on delete', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const currentStory = { ...mockStory, version: 2 }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 409,
        json: () => Promise.resolve(currentStory),
      } as Response)

      try {
        await deleteStory('family-1', 'story-1', 1)
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(StoryApiError)
        const err = e as StoryApiError
        expect(err.status).toBe(409)
        expect(err.code).toBe('VERSION_CONFLICT')
        expect(err.conflictStory).toBeDefined()
        expect(err.conflictStory!.version).toBe(2)
      }
    })
  })

  describe('getStory', () => {
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
        json: () => Promise.resolve(mockStory),
      } as Response)

      const result = await getStory('family-1', 'story-1')

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/stories/story-1',
        expect.objectContaining({
          method: 'GET',
        }),
      )
      expect(result).toEqual(mockStory)
    })

    it('handles 404 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
      } as Response)

      await expect(getStory('family-1', 'story-1')).rejects.toThrow('故事不存在')
    })
  })
})
