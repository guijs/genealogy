import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { PersonApiError, HidePersonConflictError } from './personClient'

describe('PersonApiError', () => {
  it('fromStatus maps 400 to invalid name message', () => {
    const err = PersonApiError.fromStatus(400, '/test')
    expect(err.status).toBe(400)
    expect(err.message).toBe('姓名格式无效')
  })

  it('fromStatus maps 404 to not found message', () => {
    const err = PersonApiError.fromStatus(404, '/test')
    expect(err.status).toBe(404)
    expect(err.message).toBe('成员或家族不存在')
  })

  it('fromStatus maps 409 to person cap message', () => {
    const err = PersonApiError.fromStatus(409, '/test')
    expect(err.status).toBe(409)
    expect(err.message).toBe('已达成员上限')
  })

  it('fromStatus maps unknown status to generic message', () => {
    const err = PersonApiError.fromStatus(500, '/api/test')
    expect(err.status).toBe(500)
    expect(err.message).toContain('500')
    expect(err.message).toContain('/api/test')
  })

  it('constructor sets all properties', () => {
    const err = new PersonApiError('测试错误', 401, 'TEST_CODE')
    expect(err.message).toBe('测试错误')
    expect(err.status).toBe(401)
    expect(err.code).toBe('TEST_CODE')
    expect(err.name).toBe('PersonApiError')
  })
})

describe('personClient API functions', () => {
  const originalEnv = { ...import.meta.env }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    Object.assign(import.meta.env, originalEnv)
  })

  it('createPerson throws on mock mode (VITE_USE_GRAPH_API not set)', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'false'
    import.meta.env.VITE_GRAPH_API_BASE = ''

    const { createPerson } = await import('./personClient')
    await expect(createPerson('fam-1', { first_name: '明', last_name: '陈' }))
      .rejects.toThrow('mock 模式')
  })

  it('updatePerson throws on mock mode', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'false'
    import.meta.env.VITE_GRAPH_API_BASE = ''

    const { updatePerson } = await import('./personClient')
    await expect(updatePerson('fam-1', 'p-1', { first_name: '华' }))
      .rejects.toThrow('mock 模式')
  })

  it('hidePerson throws on mock mode', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'false'
    import.meta.env.VITE_GRAPH_API_BASE = ''

    const { hidePerson } = await import('./personClient')
    await expect(hidePerson('fam-1', 'p-1'))
      .rejects.toThrow('mock 模式')
  })

  it('restorePerson throws on mock mode', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'false'
    import.meta.env.VITE_GRAPH_API_BASE = ''

    const { restorePerson } = await import('./personClient')
    await expect(restorePerson('fam-1', 'p-1'))
      .rejects.toThrow('mock 模式')
  })
})

describe('HidePersonConflictError', () => {
  it('has correct properties', () => {
    const err = new HidePersonConflictError('有活跃婚姻')
    expect(err.name).toBe('HidePersonConflictError')
    expect(err.status).toBe(409)
    expect(err.code).toBe('ACTIVE_UNION_REQUIRES_CONFIRM')
    expect(err.message).toBe('有活跃婚姻')
  })
})

describe('hidePerson API function with real API', () => {
  const originalEnv = { ...import.meta.env }

  beforeEach(() => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_USER_ID = 'test-user-id'
    import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    Object.assign(import.meta.env, originalEnv)
  })

  it('hidePerson calls correct endpoint with POST', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
    })
    vi.stubGlobal('fetch', mockFetch)

    const { hidePerson } = await import('./personClient')
    await hidePerson('fam-123', 'person-456')

    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/families/fam-123/persons/person-456/hide',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'X-User-Id': 'test-user-id',
        }),
      })
    )
  })

  it('hidePerson sends confirm flag in body when true', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
    })
    vi.stubGlobal('fetch', mockFetch)

    const { hidePerson } = await import('./personClient')
    await hidePerson('fam-123', 'person-456', true)

    expect(mockFetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        body: JSON.stringify({ confirm_hide_with_active_union: true }),
      })
    )
  })

  it('hidePerson throws HidePersonConflictError on 409', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      json: vi.fn().mockResolvedValue({ error: '有活跃婚姻需确认' }),
    })
    vi.stubGlobal('fetch', mockFetch)

    const { hidePerson, HidePersonConflictError } = await import('./personClient')
    await expect(hidePerson('fam-123', 'person-456'))
      .rejects.toThrow(HidePersonConflictError)
  })

  it('hidePerson throws PersonApiError on 404', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
    })
    vi.stubGlobal('fetch', mockFetch)

    const { hidePerson, PersonApiError } = await import('./personClient')
    await expect(hidePerson('fam-123', 'person-456'))
      .rejects.toThrow(PersonApiError)
  })
})

describe('restorePerson API function with real API', () => {
  const originalEnv = { ...import.meta.env }

  beforeEach(() => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_USER_ID = 'test-user-id'
    import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    Object.assign(import.meta.env, originalEnv)
  })

  it('restorePerson calls correct endpoint with POST', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
    })
    vi.stubGlobal('fetch', mockFetch)

    const { restorePerson } = await import('./personClient')
    await restorePerson('fam-123', 'person-456')

    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/families/fam-123/persons/person-456/restore',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'X-User-Id': 'test-user-id',
        }),
      })
    )
  })

  it('restorePerson throws PersonApiError on 404', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
    })
    vi.stubGlobal('fetch', mockFetch)

    const { restorePerson, PersonApiError } = await import('./personClient')
    await expect(restorePerson('fam-123', 'person-456'))
      .rejects.toThrow(PersonApiError)
  })
})
