import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { MemberApiError } from './memberClient'

describe('MemberApiError', () => {
  it('fromStatus maps 400 to invalid params message', () => {
    const err = MemberApiError.fromStatus(400, '/test')
    expect(err.status).toBe(400)
    expect(err.message).toBe('请求参数无效')
  })

  it('fromStatus maps 401 to auth required message', () => {
    const err = MemberApiError.fromStatus(401, '/test')
    expect(err.status).toBe(401)
    expect(err.message).toBe('需要认证')
  })

  it('fromStatus maps 403 to admin required message', () => {
    const err = MemberApiError.fromStatus(403, '/test')
    expect(err.status).toBe(403)
    expect(err.message).toBe('需要管理员权限')
  })

  it('fromStatus maps 409 to already member message', () => {
    const err = MemberApiError.fromStatus(409, '/test')
    expect(err.status).toBe(409)
    expect(err.message).toBe('该用户已是家族成员')
  })

  it('fromStatus maps unknown status to generic message', () => {
    const err = MemberApiError.fromStatus(500, '/api/test')
    expect(err.status).toBe(500)
    expect(err.message).toContain('500')
    expect(err.message).toContain('/api/test')
  })

  it('constructor sets all properties', () => {
    const err = new MemberApiError('测试错误', 401, 'TEST_CODE')
    expect(err.message).toBe('测试错误')
    expect(err.status).toBe(401)
    expect(err.code).toBe('TEST_CODE')
    expect(err.name).toBe('MemberApiError')
  })
})

describe('memberClient API functions', () => {
  const originalEnv = { ...import.meta.env }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    Object.assign(import.meta.env, originalEnv)
  })

  it('listMembers throws on mock mode (VITE_USE_GRAPH_API not set)', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'false'
    import.meta.env.VITE_GRAPH_API_BASE = ''

    const { listMembers } = await import('./memberClient')
    await expect(listMembers('test-family-id'))
      .rejects.toThrow('mock 模式')
  })

  it('addMember throws on mock mode', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'false'
    import.meta.env.VITE_GRAPH_API_BASE = ''

    const { addMember } = await import('./memberClient')
    await expect(addMember('test-family-id', { user_id: 'test-user', role: 'viewer' }))
      .rejects.toThrow('mock 模式')
  })

  it('listMembers throws on empty familyId', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_USER_ID = 'test-user-id'

    const { listMembers } = await import('./memberClient')
    await expect(listMembers(''))
      .rejects.toThrow('familyId 不能为空')
  })

  it('addMember throws on empty familyId', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_USER_ID = 'test-user-id'

    const { addMember } = await import('./memberClient')
    await expect(addMember('', { user_id: 'test-user', role: 'viewer' }))
      .rejects.toThrow('familyId 不能为空')
  })
})
