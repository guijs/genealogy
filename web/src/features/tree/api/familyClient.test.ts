import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { FamilyApiError } from './familyClient'

describe('FamilyApiError', () => {
  it('fromStatus maps 400 to invalid name message', () => {
    const err = FamilyApiError.fromStatus(400, '/test')
    expect(err.status).toBe(400)
    expect(err.message).toBe('家族名称无效')
  })

  it('fromStatus maps 401 to auth required message', () => {
    const err = FamilyApiError.fromStatus(401, '/test')
    expect(err.status).toBe(401)
    expect(err.message).toBe('需要认证')
  })

  it('fromStatus maps unknown status to generic message', () => {
    const err = FamilyApiError.fromStatus(500, '/api/test')
    expect(err.status).toBe(500)
    expect(err.message).toContain('500')
    expect(err.message).toContain('/api/test')
  })

  it('constructor sets all properties', () => {
    const err = new FamilyApiError('测试错误', 401, 'TEST_CODE')
    expect(err.message).toBe('测试错误')
    expect(err.status).toBe(401)
    expect(err.code).toBe('TEST_CODE')
    expect(err.name).toBe('FamilyApiError')
  })
})

describe('familyClient API functions', () => {
  const originalEnv = { ...import.meta.env }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    Object.assign(import.meta.env, originalEnv)
  })

  it('createFamily throws on mock mode (VITE_USE_GRAPH_API not set)', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'false'
    import.meta.env.VITE_GRAPH_API_BASE = ''

    const { createFamily } = await import('./familyClient')
    await expect(createFamily({ name: '测试家族' }))
      .rejects.toThrow('mock 模式')
  })

  it('listFamilies throws on mock mode', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'false'
    import.meta.env.VITE_GRAPH_API_BASE = ''

    const { listFamilies } = await import('./familyClient')
    await expect(listFamilies())
      .rejects.toThrow('mock 模式')
  })
})
