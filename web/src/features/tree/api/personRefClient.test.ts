import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  fetchPersonRefCandidates,
  PersonRefApiError,
} from './personRefClient'
import type { PersonRefCandidate, PersonRefCandidatesResponse } from './types'

const mockLocalStorage = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
  }
})()

const mockCandidates: PersonRefCandidate[] = [
  { person_id: 'person-1', display_name: '张三', deceased: false },
  { person_id: 'person-2', display_name: '李四', deceased: true },
]

describe('personRefClient', () => {
  describe('PersonRefApiError', () => {
    it('creates error with message and status', () => {
      const error = new PersonRefApiError('test error', 404)
      expect(error.message).toBe('test error')
      expect(error.status).toBe(404)
      expect(error.name).toBe('PersonRefApiError')
    })

    it('creates error with code', () => {
      const error = new PersonRefApiError('invalid ref', 400, 'INVALID_PERSON_REF')
      expect(error.code).toBe('INVALID_PERSON_REF')
    })

    it('creates mock mode error', () => {
      const error = PersonRefApiError.mockMode()
      expect(error.status).toBe(0)
      expect(error.code).toBe('MOCK_MODE')
      expect(error.message).toContain('mock')
    })

    it('creates invalid person ref error with Chinese message', () => {
      const error = PersonRefApiError.invalidPersonRef()
      expect(error.status).toBe(400)
      expect(error.code).toBe('INVALID_PERSON_REF')
      expect(error.message).toBe('引用的人物无效或不存在于当前家族')
    })

    it('creates error from status codes', () => {
      expect(PersonRefApiError.fromStatus(400).message).toBe('请求参数无效')
      expect(PersonRefApiError.fromStatus(401).message).toBe('请先登录')
      expect(PersonRefApiError.fromStatus(403).message).toBe('没有访问权限')
      expect(PersonRefApiError.fromStatus(404).message).toBe('未找到家族')
      expect(PersonRefApiError.fromStatus(500).message).toContain('500')
    })
  })

  describe('fetchPersonRefCandidates', () => {
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

      await expect(fetchPersonRefCandidates('family-1')).rejects.toThrow(PersonRefApiError)
      
      try {
        await fetchPersonRefCandidates('family-1')
      } catch (e) {
        expect((e as PersonRefApiError).code).toBe('MOCK_MODE')
      }
    })

    it('throws error when familyId is empty', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(fetchPersonRefCandidates('')).rejects.toThrow(PersonRefApiError)
      
      try {
        await fetchPersonRefCandidates('')
      } catch (e) {
        expect((e as PersonRefApiError).code).toBe('INVALID_FAMILY_ID')
      }
    })

    it('makes correct API call and returns candidates', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse: PersonRefCandidatesResponse = { candidates: mockCandidates }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await fetchPersonRefCandidates('family-1')

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/person-ref-candidates',
        expect.objectContaining({
          method: 'GET',
          headers: expect.any(Object),
        }),
      )
      expect(result.candidates).toHaveLength(2)
      expect(result.candidates[0].person_id).toBe('person-1')
      expect(result.candidates[1].deceased).toBe(true)
    })

    it('handles 401 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 401,
      } as Response)

      await expect(fetchPersonRefCandidates('family-1')).rejects.toThrow('请先登录')
    })

    it('handles 403 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 403,
      } as Response)

      await expect(fetchPersonRefCandidates('family-1')).rejects.toThrow('没有访问权限')
    })

    it('handles 404 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
      } as Response)

      await expect(fetchPersonRefCandidates('family-1')).rejects.toThrow('未找到家族')
    })

    it('returns empty candidates array when no persons', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse: PersonRefCandidatesResponse = { candidates: [] }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await fetchPersonRefCandidates('family-1')

      expect(result.candidates).toHaveLength(0)
    })
  })
})
