import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { fetchGenerations, getGenerationLabel, GenerationsApiError } from './generationsClient'

const mockLocalStorage = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
  }
})()

describe('generationsClient', () => {
  describe('getGenerationLabel', () => {
    it('returns "本代 (0)" for index 0', () => {
      expect(getGenerationLabel(0)).toBe('本代 (0)')
    })

    it('returns "上一代 (-1)" for index -1', () => {
      expect(getGenerationLabel(-1)).toBe('上一代 (-1)')
    })

    it('returns "上2代 (-2)" for index -2', () => {
      expect(getGenerationLabel(-2)).toBe('上2代 (-2)')
    })

    it('returns "下一代 (+1)" for index 1', () => {
      expect(getGenerationLabel(1)).toBe('下一代 (+1)')
    })

    it('returns "下2代 (+2)" for index 2', () => {
      expect(getGenerationLabel(2)).toBe('下2代 (+2)')
    })

    it('handles large ancestor indices', () => {
      expect(getGenerationLabel(-5)).toBe('上5代 (-5)')
    })

    it('handles large descendant indices', () => {
      expect(getGenerationLabel(5)).toBe('下5代 (+5)')
    })
  })

  describe('GenerationsApiError', () => {
    it('creates error with message and status', () => {
      const error = new GenerationsApiError('test error', 404)
      expect(error.message).toBe('test error')
      expect(error.status).toBe(404)
      expect(error.name).toBe('GenerationsApiError')
    })
  })

  describe('fetchGenerations', () => {
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

      await expect(fetchGenerations({ familyId: 'test-id' })).rejects.toThrow(
        GenerationsApiError,
      )
    })

    it('throws error when familyId is empty', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(fetchGenerations({ familyId: '' })).rejects.toThrow(
        GenerationsApiError,
      )
    })

    it('makes correct API call with focusPersonId', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        familyId: 'family-1',
        focusPersonId: 'person-1',
        generations: [
          { index: 0, persons: [{ id: 'person-1', displayName: 'Test' }] },
        ],
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await fetchGenerations({
        familyId: 'family-1',
        focusPersonId: 'person-1',
      })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/generations?focusPersonId=person-1',
        expect.objectContaining({
          headers: expect.any(Object),
        }),
      )
      expect(result).toEqual(mockResponse)
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
        fetchGenerations({ familyId: 'family-1' }),
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
        fetchGenerations({ familyId: 'family-1' }),
      ).rejects.toThrow('未找到家族或人物')
    })

    it('handles 404 error for no persons in family', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ error: 'no persons in family' }),
      } as Response)

      await expect(
        fetchGenerations({ familyId: 'family-1' }),
      ).rejects.toThrow('家族暂无成员')
    })
  })
})
