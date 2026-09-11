import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { PersonApiError } from './personClient'

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
})
