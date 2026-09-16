import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  fetchGenerationNames,
  updateGenerationNames,
  validateGenerationNames,
  GenerationNamesApiError,
} from './generationNamesClient'

const mockLocalStorage = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
  }
})()

describe('generationNamesClient', () => {
  describe('GenerationNamesApiError', () => {
    it('creates error with message and status', () => {
      const error = new GenerationNamesApiError('test error', 404)
      expect(error.message).toBe('test error')
      expect(error.status).toBe(404)
      expect(error.name).toBe('GenerationNamesApiError')
    })

    it('creates error with code', () => {
      const error = new GenerationNamesApiError('admin required', 403, 'ADMIN_REQUIRED')
      expect(error.code).toBe('ADMIN_REQUIRED')
    })
  })

  describe('validateGenerationNames', () => {
    it('returns null for valid names', () => {
      expect(validateGenerationNames(['德', '明', '仁', '义'])).toBeNull()
    })

    it('returns null for empty array', () => {
      expect(validateGenerationNames([])).toBeNull()
    })

    it('returns null for array with empty strings (空档)', () => {
      expect(validateGenerationNames(['德', '', '仁', ''])).toBeNull()
    })

    it('returns error for more than 200 entries', () => {
      const names = Array(201).fill('德')
      expect(validateGenerationNames(names)).toBe('字辈数量超过上限（最多200个）')
    })

    it('returns error for entry longer than 16 chars', () => {
      const longName = '德'.repeat(17)
      expect(validateGenerationNames([longName])).toBe('第1个字辈过长（最多16字符）')
    })

    it('returns error with correct index for long entry', () => {
      const names = ['德', '明', '这是一个超过十六个字符的很长的字符串']
      expect(validateGenerationNames(names)).toBe('第3个字辈过长（最多16字符）')
    })
  })

  describe('fetchGenerationNames', () => {
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

      await expect(fetchGenerationNames({ familyId: 'test-id' })).rejects.toThrow(
        GenerationNamesApiError,
      )
    })

    it('throws error when familyId is empty', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      await expect(fetchGenerationNames({ familyId: '' })).rejects.toThrow(
        GenerationNamesApiError,
      )
    })

    it('makes correct API call', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        generation_names: ['德', '明', '仁', '义'],
        generation_name_align: 'A',
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await fetchGenerationNames({ familyId: 'family-1' })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/generation-names',
        expect.objectContaining({
          headers: expect.any(Object),
        }),
      )
      expect(result).toEqual(mockResponse)
    })

    it('handles null generation_names (not configured)', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        generation_names: null,
        generation_name_align: 'A',
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await fetchGenerationNames({ familyId: 'family-1' })

      expect(result.generation_names).toBeNull()
      expect(result.generation_name_align).toBe('A')
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
        fetchGenerationNames({ familyId: 'family-1' }),
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
        fetchGenerationNames({ familyId: 'family-1' }),
      ).rejects.toThrow('未找到家族')
    })
  })

  describe('updateGenerationNames', () => {
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

      await expect(
        updateGenerationNames({ familyId: 'test-id', generationNames: ['德'] }),
      ).rejects.toThrow(GenerationNamesApiError)
    })

    it('makes correct API call with names only', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        generation_names: ['德', '明', '仁'],
        generation_name_align: 'A',
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await updateGenerationNames({
        familyId: 'family-1',
        generationNames: ['德', '明', '仁'],
      })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/generation-names',
        expect.objectContaining({
          method: 'PUT',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
          body: JSON.stringify({ generation_names: ['德', '明', '仁'] }),
        }),
      )
      expect(result).toEqual(mockResponse)
    })

    it('makes correct API call with names and align', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        generation_names: ['德', '明'],
        generation_name_align: 'B',
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      await updateGenerationNames({
        familyId: 'family-1',
        generationNames: ['德', '明'],
        generationNameAlign: 'B',
      })

      expect(fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/families/family-1/generation-names',
        expect.objectContaining({
          body: JSON.stringify({
            generation_names: ['德', '明'],
            generation_name_align: 'B',
          }),
        }),
      )
    })

    it('handles empty names array (clear)', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const mockResponse = {
        generation_names: [],
        generation_name_align: 'A',
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      } as Response)

      const result = await updateGenerationNames({
        familyId: 'family-1',
        generationNames: [],
      })

      expect(result.generation_names).toEqual([])
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
        await updateGenerationNames({ familyId: 'family-1', generationNames: ['德'] })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(GenerationNamesApiError)
        const err = e as GenerationNamesApiError
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
        await updateGenerationNames({ familyId: 'family-1', generationNames: ['德'] })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(GenerationNamesApiError)
        const err = e as GenerationNamesApiError
        expect(err.message).toBe('没有编辑权限')
        expect(err.code).toBe('WRITE_ACCESS_REQUIRED')
      }
    })

    it('handles 400 too many entries error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ error: 'too many generation names' }),
      } as Response)

      try {
        await updateGenerationNames({ familyId: 'family-1', generationNames: Array(201).fill('德') })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(GenerationNamesApiError)
        const err = e as GenerationNamesApiError
        expect(err.message).toBe('字辈数量超过上限（最多200个）')
        expect(err.code).toBe('TOO_MANY_ENTRIES')
      }
    })

    it('handles 400 entry too long error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ error: 'entry too long' }),
      } as Response)

      try {
        await updateGenerationNames({ familyId: 'family-1', generationNames: ['德'.repeat(20)] })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(GenerationNamesApiError)
        const err = e as GenerationNamesApiError
        expect(err.message).toBe('单个字辈字符过长（最多16字符）')
        expect(err.code).toBe('ENTRY_TOO_LONG')
      }
    })

    it('handles 404 not found error', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ error: 'not found' }),
      } as Response)

      try {
        await updateGenerationNames({ familyId: 'invalid-family', generationNames: ['德'] })
        expect.fail('Expected error to be thrown')
      } catch (e) {
        expect(e).toBeInstanceOf(GenerationNamesApiError)
        const err = e as GenerationNamesApiError
        expect(err.message).toBe('未找到家族')
        expect(err.code).toBe('NOT_FOUND')
      }
    })
  })

  /**
   * Error isolation: updateGenerationNames errors should NOT affect lineage data.
   * UI uses separate actionError for write failures, loadError for load failures.
   */
  describe('fetchGenerationNames + updateGenerationNames error isolation', () => {
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

    it('updateGenerationNames 403 does not invalidate previously fetched config', async () => {
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
      mockLocalStorage.setItem('auth_token', 'test-token')

      const configData = {
        generation_names: ['德', '明', '仁', '义'],
        generation_name_align: 'A',
      }

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(configData),
      } as Response)

      const config = await fetchGenerationNames({ familyId: 'family-1' })
      expect(config.generation_names).toEqual(['德', '明', '仁', '义'])

      vi.mocked(fetch).mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: () => Promise.resolve({ error: 'admin access required' }),
      } as Response)

      let putError: GenerationNamesApiError | null = null
      try {
        await updateGenerationNames({
          familyId: 'family-1',
          generationNames: ['新', '字', '辈'],
        })
      } catch (e) {
        putError = e as GenerationNamesApiError
      }

      expect(putError).not.toBeNull()
      expect(putError!.status).toBe(403)
      expect(putError!.code).toBe('ADMIN_REQUIRED')

      expect(config.generation_names).toEqual(['德', '明', '仁', '义'])
    })
  })
})
