import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import {
  MediaApiError,
  ALLOWED_MIME_TYPES,
  MAX_FILE_SIZE,
  MIN_FILE_SIZE,
  isValidMimeType,
  isValidFileSize,
  validateFile,
} from './mediaClient'

describe('MediaApiError', () => {
  it('fromStatus maps 400 to invalid params message', () => {
    const err = MediaApiError.fromStatus(400, '/test')
    expect(err.status).toBe(400)
    expect(err.message).toBe('请求参数无效')
  })

  it('fromStatus maps 401 to auth required message', () => {
    const err = MediaApiError.fromStatus(401, '/test')
    expect(err.status).toBe(401)
    expect(err.message).toBe('需要认证')
  })

  it('fromStatus maps 403 to no permission message', () => {
    const err = MediaApiError.fromStatus(403, '/test')
    expect(err.status).toBe(403)
    expect(err.message).toBe('无权限上传到此家族')
  })

  it('fromStatus maps 404 to family not found message', () => {
    const err = MediaApiError.fromStatus(404, '/test')
    expect(err.status).toBe(404)
    expect(err.message).toBe('家族不存在')
  })

  it('fromStatus maps 413 to file too large message', () => {
    const err = MediaApiError.fromStatus(413, '/test')
    expect(err.status).toBe(413)
    expect(err.message).toBe('文件过大')
  })

  it('fromStatus maps unknown status to generic message', () => {
    const err = MediaApiError.fromStatus(500, '/api/test')
    expect(err.status).toBe(500)
    expect(err.message).toContain('500')
    expect(err.message).toContain('/api/test')
  })

  it('invalidMimeType creates error with INVALID_MIME_TYPE code', () => {
    const err = MediaApiError.invalidMimeType('image/gif')
    expect(err.status).toBe(0)
    expect(err.code).toBe('INVALID_MIME_TYPE')
    expect(err.message).toContain('image/gif')
    expect(err.message).toContain('JPEG')
    expect(err.message).toContain('PNG')
    expect(err.message).toContain('WebP')
  })

  it('fileTooLarge creates error with FILE_TOO_LARGE code', () => {
    const fileSize = 10 * 1024 * 1024
    const err = MediaApiError.fileTooLarge(fileSize)
    expect(err.status).toBe(0)
    expect(err.code).toBe('FILE_TOO_LARGE')
    expect(err.message).toContain('10.00MB')
    expect(err.message).toContain('5MB')
  })

  it('fileEmpty creates error with FILE_EMPTY code', () => {
    const err = MediaApiError.fileEmpty()
    expect(err.status).toBe(0)
    expect(err.code).toBe('FILE_EMPTY')
    expect(err.message).toContain('不能为空')
  })

  it('constructor sets all properties', () => {
    const err = new MediaApiError('测试错误', 401, 'TEST_CODE')
    expect(err.message).toBe('测试错误')
    expect(err.status).toBe(401)
    expect(err.code).toBe('TEST_CODE')
    expect(err.name).toBe('MediaApiError')
  })
})

describe('MIME type validation', () => {
  it('allows jpeg', () => {
    expect(isValidMimeType('image/jpeg')).toBe(true)
  })

  it('allows png', () => {
    expect(isValidMimeType('image/png')).toBe(true)
  })

  it('allows webp', () => {
    expect(isValidMimeType('image/webp')).toBe(true)
  })

  it('rejects gif', () => {
    expect(isValidMimeType('image/gif')).toBe(false)
  })

  it('rejects svg', () => {
    expect(isValidMimeType('image/svg+xml')).toBe(false)
  })

  it('rejects non-image types', () => {
    expect(isValidMimeType('application/pdf')).toBe(false)
    expect(isValidMimeType('text/plain')).toBe(false)
    expect(isValidMimeType('video/mp4')).toBe(false)
  })

  it('ALLOWED_MIME_TYPES contains exactly jpeg, png, webp', () => {
    expect(ALLOWED_MIME_TYPES).toEqual(['image/jpeg', 'image/png', 'image/webp'])
  })
})

describe('file size validation', () => {
  it('rejects zero size', () => {
    expect(isValidFileSize(0)).toBe(false)
  })

  it('accepts minimum size (1 byte)', () => {
    expect(isValidFileSize(MIN_FILE_SIZE)).toBe(true)
    expect(isValidFileSize(1)).toBe(true)
  })

  it('accepts exactly 5MB', () => {
    expect(isValidFileSize(MAX_FILE_SIZE)).toBe(true)
    expect(isValidFileSize(5 * 1024 * 1024)).toBe(true)
  })

  it('rejects over 5MB', () => {
    expect(isValidFileSize(MAX_FILE_SIZE + 1)).toBe(false)
    expect(isValidFileSize(6 * 1024 * 1024)).toBe(false)
  })

  it('accepts typical image sizes', () => {
    expect(isValidFileSize(100 * 1024)).toBe(true)
    expect(isValidFileSize(1024 * 1024)).toBe(true)
    expect(isValidFileSize(3 * 1024 * 1024)).toBe(true)
  })

  it('MIN_FILE_SIZE is 1', () => {
    expect(MIN_FILE_SIZE).toBe(1)
  })

  it('MAX_FILE_SIZE is 5MB', () => {
    expect(MAX_FILE_SIZE).toBe(5 * 1024 * 1024)
  })
})

describe('validateFile', () => {
  it('throws FILE_EMPTY for zero size', () => {
    expect(() => validateFile('image/jpeg', 0)).toThrow(MediaApiError)
    try {
      validateFile('image/jpeg', 0)
    } catch (e) {
      expect((e as MediaApiError).code).toBe('FILE_EMPTY')
    }
  })

  it('throws FILE_TOO_LARGE for over 5MB', () => {
    const largeSize = 6 * 1024 * 1024
    expect(() => validateFile('image/jpeg', largeSize)).toThrow(MediaApiError)
    try {
      validateFile('image/jpeg', largeSize)
    } catch (e) {
      expect((e as MediaApiError).code).toBe('FILE_TOO_LARGE')
    }
  })

  it('throws INVALID_MIME_TYPE for gif', () => {
    expect(() => validateFile('image/gif', 1024)).toThrow(MediaApiError)
    try {
      validateFile('image/gif', 1024)
    } catch (e) {
      expect((e as MediaApiError).code).toBe('INVALID_MIME_TYPE')
    }
  })

  it('does not throw for valid jpeg file', () => {
    expect(() => validateFile('image/jpeg', 1024)).not.toThrow()
  })

  it('does not throw for valid png file', () => {
    expect(() => validateFile('image/png', 2 * 1024 * 1024)).not.toThrow()
  })

  it('does not throw for valid webp file', () => {
    expect(() => validateFile('image/webp', MAX_FILE_SIZE)).not.toThrow()
  })

  it('checks size before mime type (empty file fails first)', () => {
    try {
      validateFile('image/gif', 0)
    } catch (e) {
      expect((e as MediaApiError).code).toBe('FILE_EMPTY')
    }
  })
})

describe('mediaClient API functions', () => {
  const originalEnv = { ...import.meta.env }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    Object.assign(import.meta.env, originalEnv)
  })

  it('requestUploadUrl throws on mock mode (VITE_USE_GRAPH_API not set)', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'false'
    import.meta.env.VITE_GRAPH_API_BASE = ''

    const { requestUploadUrl } = await import('./mediaClient')
    await expect(requestUploadUrl('fam-1', 'image/jpeg', 1024))
      .rejects.toThrow('mock 模式')
  })

  it('requestUploadUrl validates mime type before API call', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_API_BASE = 'https://api.example.com'
    const mockStorage: Record<string, string> = { auth_token: 'test-jwt-token' }
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => mockStorage[key] ?? null,
      setItem: (key: string, value: string) => { mockStorage[key] = value },
      removeItem: (key: string) => { delete mockStorage[key] },
      clear: () => { Object.keys(mockStorage).forEach(key => delete mockStorage[key]) },
    })

    const { requestUploadUrl } = await import('./mediaClient')
    await expect(requestUploadUrl('fam-1', 'image/gif', 1024))
      .rejects.toThrow('不支持的文件类型')
  })

  it('requestUploadUrl validates file size before API call', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_API_BASE = 'https://api.example.com'
    const mockStorage: Record<string, string> = { auth_token: 'test-jwt-token' }
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => mockStorage[key] ?? null,
      setItem: (key: string, value: string) => { mockStorage[key] = value },
      removeItem: (key: string) => { delete mockStorage[key] },
      clear: () => { Object.keys(mockStorage).forEach(key => delete mockStorage[key]) },
    })

    const { requestUploadUrl } = await import('./mediaClient')
    await expect(requestUploadUrl('fam-1', 'image/jpeg', 10 * 1024 * 1024))
      .rejects.toThrow('文件过大')
  })

  it('requestUploadUrl throws AUTH_MISSING when not logged in', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_API_BASE = 'https://api.example.com'
    const mockStorage: Record<string, string> = {}
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => mockStorage[key] ?? null,
      setItem: (key: string, value: string) => { mockStorage[key] = value },
      removeItem: (key: string) => { delete mockStorage[key] },
      clear: () => { Object.keys(mockStorage).forEach(key => delete mockStorage[key]) },
    })

    const { requestUploadUrl } = await import('./mediaClient')
    await expect(requestUploadUrl('fam-1', 'image/jpeg', 1024))
      .rejects.toThrow('请先登录')
  })
})

describe('request body contract', () => {
  const mockStorage: Record<string, string> = {}

  beforeEach(() => {
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => mockStorage[key] ?? null,
      setItem: (key: string, value: string) => { mockStorage[key] = value },
      removeItem: (key: string) => { delete mockStorage[key] },
      clear: () => { Object.keys(mockStorage).forEach(key => delete mockStorage[key]) },
    })
    mockStorage['auth_token'] = 'test-jwt-token'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    Object.keys(mockStorage).forEach(key => delete mockStorage[key])
  })

  it('RequestUploadUrlRequest uses snake_case fields', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_API_BASE = 'https://api.example.com'

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ upload_url: 'https://s3.example.com/upload', storage_key: 'media/123' }),
    })
    vi.stubGlobal('fetch', mockFetch)

    const { requestUploadUrl } = await import('./mediaClient')
    await requestUploadUrl('fam-1', 'image/jpeg', 1024)

    expect(mockFetch).toHaveBeenCalledTimes(1)
    const [url, options] = mockFetch.mock.calls[0]

    expect(url).toContain('/api/v1/families/fam-1/media/upload-url')

    const body = JSON.parse(options.body)
    expect(body).toHaveProperty('mime_type', 'image/jpeg')
    expect(body).toHaveProperty('file_size', 1024)
    expect(body).not.toHaveProperty('storage_key')
    expect(body).not.toHaveProperty('storageKey')
    expect(body).not.toHaveProperty('mimeType')
    expect(body).not.toHaveProperty('fileSize')
  })

  it('request includes Authorization Bearer header', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_API_BASE = 'https://api.example.com'

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ upload_url: 'https://s3.example.com/upload', storage_key: 'media/123' }),
    })
    vi.stubGlobal('fetch', mockFetch)

    const { requestUploadUrl } = await import('./mediaClient')
    await requestUploadUrl('fam-1', 'image/png', 2048)

    const [, options] = mockFetch.mock.calls[0]
    expect(options.headers).toHaveProperty('Authorization', 'Bearer test-jwt-token')
    expect(options.headers).toHaveProperty('Content-Type', 'application/json')
  })

  it('URL contains familyId in path', async () => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_API_BASE = 'https://api.example.com'

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ upload_url: 'https://s3.example.com/upload', storage_key: 'media/123' }),
    })
    vi.stubGlobal('fetch', mockFetch)

    const { requestUploadUrl } = await import('./mediaClient')
    await requestUploadUrl('family-abc-123', 'image/webp', 4096)

    const [url] = mockFetch.mock.calls[0]
    expect(url).toBe('https://api.example.com/api/v1/families/family-abc-123/media/upload-url')
  })
})

describe('response handling', () => {
  const mockStorage: Record<string, string> = {}

  beforeEach(() => {
    import.meta.env.VITE_USE_GRAPH_API = 'true'
    import.meta.env.VITE_GRAPH_API_BASE = 'https://api.example.com'
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => mockStorage[key] ?? null,
      setItem: (key: string, value: string) => { mockStorage[key] = value },
      removeItem: (key: string) => { delete mockStorage[key] },
      clear: () => { Object.keys(mockStorage).forEach(key => delete mockStorage[key]) },
    })
    mockStorage['auth_token'] = 'test-jwt-token'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    Object.keys(mockStorage).forEach(key => delete mockStorage[key])
  })

  it('returns upload_url and storage_key from response', async () => {
    const mockResponse = {
      upload_url: 'https://s3.amazonaws.com/bucket/path?signature=abc',
      storage_key: 'families/fam-1/media/uuid-123.jpg',
    }
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockResponse),
    })
    vi.stubGlobal('fetch', mockFetch)

    const { requestUploadUrl } = await import('./mediaClient')
    const result = await requestUploadUrl('fam-1', 'image/jpeg', 1024)

    expect(result.upload_url).toBe(mockResponse.upload_url)
    expect(result.storage_key).toBe(mockResponse.storage_key)
  })

  it('throws MediaApiError on non-ok response', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
    })
    vi.stubGlobal('fetch', mockFetch)

    const { requestUploadUrl } = await import('./mediaClient')
    await expect(requestUploadUrl('fam-1', 'image/jpeg', 1024))
      .rejects.toThrow('无权限上传')
  })
})

describe('putUploadFile', () => {
  beforeEach(() => {
    import.meta.env.VITE_GRAPH_API_BASE = 'https://api.example.com'
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends PUT request with Content-Type header and no Authorization', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 201 })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test content'], 'test.jpg', { type: 'image/jpeg' })

    const { putUploadFile } = await import('./mediaClient')
    await putUploadFile('https://api.example.com/api/v1/media/uploads/abc123', file)

    expect(mockFetch).toHaveBeenCalledTimes(1)
    const [url, options] = mockFetch.mock.calls[0]

    expect(url).toBe('https://api.example.com/api/v1/media/uploads/abc123')
    expect(options.method).toBe('PUT')
    expect(options.headers).toEqual({ 'Content-Type': 'image/jpeg' })
    expect(options.headers).not.toHaveProperty('Authorization')
    expect(options.body).toBe(file)
  })

  it('resolves relative upload_url against VITE_GRAPH_API_BASE', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 201 })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test'], 'test.png', { type: 'image/png' })

    const { putUploadFile } = await import('./mediaClient')
    await putUploadFile('/api/v1/media/uploads/token123', file)

    const [url] = mockFetch.mock.calls[0]
    expect(url).toBe('https://api.example.com/api/v1/media/uploads/token123')
  })

  it('throws PUT_CONTENT_TYPE_MISMATCH on 400 with Content-Type error', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: () => Promise.resolve({ error: 'Content-Type mismatch: expected image/png' }),
    })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })

    const { putUploadFile, MediaApiError } = await import('./mediaClient')

    await expect(putUploadFile('https://api.example.com/upload', file))
      .rejects.toThrow('文件类型不匹配')

    try {
      await putUploadFile('https://api.example.com/upload', file)
    } catch (e) {
      expect((e as typeof MediaApiError.prototype).code).toBe('PUT_CONTENT_TYPE_MISMATCH')
      expect((e as typeof MediaApiError.prototype).status).toBe(400)
    }
  })

  it('throws PUT_INVALID_PARAMS on 400 with non-Content-Type error', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: () => Promise.resolve({ error: 'file is empty' }),
    })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })

    const { putUploadFile, MediaApiError } = await import('./mediaClient')

    await expect(putUploadFile('https://api.example.com/upload', file))
      .rejects.toThrow('上传参数无效：file is empty')

    try {
      await putUploadFile('https://api.example.com/upload', file)
    } catch (e) {
      expect((e as typeof MediaApiError.prototype).code).toBe('PUT_INVALID_PARAMS')
      expect((e as typeof MediaApiError.prototype).status).toBe(400)
    }
  })

  it('throws PUT_INVALID_PARAMS on 400 with no parseable body', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: () => Promise.reject(new Error('not JSON')),
      text: () => Promise.reject(new Error('no body')),
    })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })

    const { putUploadFile, MediaApiError } = await import('./mediaClient')

    await expect(putUploadFile('https://api.example.com/upload', file))
      .rejects.toThrow('上传参数无效')

    try {
      await putUploadFile('https://api.example.com/upload', file)
    } catch (e) {
      expect((e as typeof MediaApiError.prototype).code).toBe('PUT_INVALID_PARAMS')
      expect((e as typeof MediaApiError.prototype).status).toBe(400)
      expect((e as typeof MediaApiError.prototype).message).toBe('上传参数无效')
    }
  })

  it('throws PUT_INVALID_PARAMS on 400 with missing Content-Length text', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: () => Promise.reject(new Error('not JSON')),
      text: () => Promise.resolve('Missing Content-Length header'),
    })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })

    const { putUploadFile, MediaApiError } = await import('./mediaClient')

    await expect(putUploadFile('https://api.example.com/upload', file))
      .rejects.toThrow('上传参数无效：Missing Content-Length header')

    try {
      await putUploadFile('https://api.example.com/upload', file)
    } catch (e) {
      expect((e as typeof MediaApiError.prototype).code).toBe('PUT_INVALID_PARAMS')
    }
  })

  it('throws PUT_INVALID_TOKEN on 401', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: false, status: 401, statusText: 'Unauthorized' })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })

    const { putUploadFile, MediaApiError } = await import('./mediaClient')

    await expect(putUploadFile('https://api.example.com/upload', file))
      .rejects.toThrow('上传链接无效或已过期')

    try {
      await putUploadFile('https://api.example.com/upload', file)
    } catch (e) {
      expect((e as typeof MediaApiError.prototype).code).toBe('PUT_INVALID_TOKEN')
      expect((e as typeof MediaApiError.prototype).status).toBe(401)
    }
  })

  it('throws PUT_OVERSIZED on 413', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: false, status: 413, statusText: 'Payload Too Large' })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })

    const { putUploadFile, MediaApiError } = await import('./mediaClient')

    await expect(putUploadFile('https://api.example.com/upload', file))
      .rejects.toThrow('文件过大')

    try {
      await putUploadFile('https://api.example.com/upload', file)
    } catch (e) {
      expect((e as typeof MediaApiError.prototype).code).toBe('PUT_OVERSIZED')
      expect((e as typeof MediaApiError.prototype).status).toBe(413)
    }
  })

  it('throws PUT_SERVER_ERROR on 5xx', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: false, status: 500, statusText: 'Internal Server Error' })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })

    const { putUploadFile, MediaApiError } = await import('./mediaClient')

    await expect(putUploadFile('https://api.example.com/upload', file))
      .rejects.toThrow('服务器错误')

    try {
      await putUploadFile('https://api.example.com/upload', file)
    } catch (e) {
      expect((e as typeof MediaApiError.prototype).code).toBe('PUT_SERVER_ERROR')
      expect((e as typeof MediaApiError.prototype).status).toBe(500)
    }
  })

  it('throws PUT_NETWORK_ERROR on fetch failure', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error('Network timeout'))
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['test'], 'test.jpg', { type: 'image/jpeg' })

    const { putUploadFile, MediaApiError } = await import('./mediaClient')

    await expect(putUploadFile('https://api.example.com/upload', file))
      .rejects.toThrow('网络错误')

    try {
      await putUploadFile('https://api.example.com/upload', file)
    } catch (e) {
      expect((e as typeof MediaApiError.prototype).code).toBe('PUT_NETWORK_ERROR')
    }
  })

  it('succeeds on 201 Created', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 201 })
    vi.stubGlobal('fetch', mockFetch)

    const file = new File(['image data'], 'photo.webp', { type: 'image/webp' })

    const { putUploadFile } = await import('./mediaClient')
    await expect(putUploadFile('https://api.example.com/upload', file)).resolves.toBeUndefined()
  })

  it('uploadToPresignedUrl is an alias for putUploadFile', async () => {
    const { putUploadFile, uploadToPresignedUrl } = await import('./mediaClient')
    expect(uploadToPresignedUrl).toBe(putUploadFile)
  })
})
