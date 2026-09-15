import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { fetchLineage, setProgenitor, getLineageLabel, LineageApiError } from './lineageClient'

const mockLocalStorage = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
  }
})()

describe('lineageClient', () => {
  describe('getLineageLabel', () => {
    it('returns "第1世（始祖）" for index 1', () => {
      expect(getLineageLabel(1)).toBe('第1世（始祖）')
    })

    it('returns "第2世" for index 2', () => {
      expect(getLineageLabel(2)).toBe('第2世')
    })

    it('returns "第3世" for index 3', () => {
      expect(getLineageLabel(3)).toBe('第3世')
    })

    it('handles large indices', () => {
      expect(getLineageLabel(10)).toBe('第10世')
    })
  })

  describe('LineageApiError', () => {
    it('creates error with message and status', () => {
      const error = new LineageApiError('test error', 404)
      expect(error.message).toBe('test error')
      expect(error.status).toBe(404)
      expect(error.name).toBe('LineageApiError')
    })

    it('creates error with code', () => {
      const error = new LineageApiError('admin required', 403, 'ADMIN_REQUIRED')
      expect(error.code).toBe('ADMIN_REQUIRED')
    })
  })

  describe('fetchLineage', () => {
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

      await expect(fetchLineage({ familyId: 'test-id' })).rejects.toThrow(
        LineageApiError,
      )
    })

    it('throws error when familyId is empty', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(fetchLineage({ familyId: '' })).rejects.toThrow(
        LineageApiError,
      )
    })

    it('makes correct API call', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        family_id: 'family-1',
        progenitor_person_id: 'progenitor-1',
        generations: [
          { index: 1, persons: [{ id: 'progenitor-1', display_name: 'Test', conflict: false }] },
        ],
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await fetchLineage({ familyId: 'family-1' })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/lineage',
        expect.objectContaining({
          headers: expect.any(Object),
        }),
      )
      expect(result).toEqual(mockResponse)
    })

    it('handles empty generations when no progenitor', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        family_id: 'family-1',
        progenitor_person_id: null,
        generations: [],
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await fetchLineage({ familyId: 'family-1' })

      expect(result.progenitor_person_id).toBeNull()
      expect(result.generations).toEqual([])
    })

    it('handles 401 error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ error: 'authentication required' }),
      } as Response)

      await expect(
        fetchLineage({ familyId: 'family-1' }),
      ).rejects.toThrow('请先登录')
    })

    it('handles 404 error for not found', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ error: 'not found' }),
      } as Response)

      await expect(
        fetchLineage({ familyId: 'family-1' }),
      ).rejects.toThrow('未找到家族')
    })
  })

  describe('setProgenitor', () => {
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

      await expect(setProgenitor({ familyId: 'test-id', personId: 'person-1' })).rejects.toThrow(
        LineageApiError,
      )
    })

    it('makes correct API call to set progenitor', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        progenitor_person_id: 'progenitor-1',
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await setProgenitor({
        familyId: 'family-1',
        personId: 'progenitor-1',
      })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/progenitor',
        expect.objectContaining({
          method: 'PUT',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
          body: JSON.stringify({ person_id: 'progenitor-1' }),
        }),
      )
      expect(result).toEqual(mockResponse)
    })

    it('makes correct API call to clear progenitor', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        progenitor_person_id: null,
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await setProgenitor({
        familyId: 'family-1',
        personId: null,
      })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/progenitor',
        expect.objectContaining({
          method: 'PUT',
          body: JSON.stringify({ person_id: null }),
        }),
      )
      expect(result.progenitor_person_id).toBeNull()
    })

    it('handles 403 admin access required error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: () => Promise.resolve({ error: 'admin access required' }),
      } as Response)

      try {
        await setProgenitor({ familyId: 'family-1', personId: 'person-1' })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(LineageApiError)
        const err = e as LineageApiError
        expect(err.message).toBe('需要管理员权限')
        expect(err.code).toBe('ADMIN_REQUIRED')
        expect(err.status).toBe(403)
      }
    })

    it('handles 403 write access required error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: () => Promise.resolve({ error: 'write access required' }),
      } as Response)

      try {
        await setProgenitor({ familyId: 'family-1', personId: 'person-1' })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(LineageApiError)
        const err = e as LineageApiError
        expect(err.message).toBe('没有编辑权限')
        expect(err.code).toBe('WRITE_ACCESS_REQUIRED')
      }
    })

    it('handles 400 hidden person error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ error: 'cannot set hidden person as progenitor' }),
      } as Response)

      try {
        await setProgenitor({ familyId: 'family-1', personId: 'hidden-person' })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(LineageApiError)
        const err = e as LineageApiError
        expect(err.message).toBe('不能将已隐藏的成员设为始迁祖')
        expect(err.code).toBe('HIDDEN_PERSON')
      }
    })

    it('handles 400 person not in family error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ error: 'person not in family' }),
      } as Response)

      try {
        await setProgenitor({ familyId: 'family-1', personId: 'other-family-person' })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(LineageApiError)
        const err = e as LineageApiError
        expect(err.message).toBe('该成员不属于此家族')
        expect(err.code).toBe('PERSON_NOT_IN_FAMILY')
      }
    })

    it('handles 404 person not found error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ error: 'person not found' }),
      } as Response)

      try {
        await setProgenitor({ familyId: 'family-1', personId: 'non-existent-person' })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(LineageApiError)
        const err = e as LineageApiError
        expect(err.message).toBe('未找到该成员')
        expect(err.code).toBe('PERSON_NOT_FOUND')
      }
    })
  })
})
