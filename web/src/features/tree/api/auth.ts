/**
 * 共享认证模块 — 为所有真实 API 客户端提供统一的 Bearer token 认证头
 *
 * 使用方式：
 * - 真实 API 路径：使用 getAuthHeaders() 获取 { Authorization: 'Bearer <token>' }
 * - Mock 模式：不需要认证头
 *
 * 硬约束：
 * - 移除 X-User-Id，改用 Authorization: Bearer <token>
 * - 未登录时抛出 AuthRequiredError
 *
 * 注意：此模块直接读取 localStorage 以支持测试环境（无需 Pinia 初始化）
 */

const AUTH_TOKEN_KEY = 'auth_token'
const AUTH_USER_ID_KEY = 'auth_user_id'

export class AuthRequiredError extends Error {
  readonly status = 401
  readonly code = 'AUTH_REQUIRED'

  constructor(message: string = '请先登录') {
    super(message)
    this.name = 'AuthRequiredError'
  }
}

/**
 * 从 localStorage 获取 token
 */
function getStoredToken(): string | null {
  if (typeof localStorage === 'undefined') return null
  return localStorage.getItem(AUTH_TOKEN_KEY)
}

/**
 * 从 localStorage 获取 userId
 */
function getStoredUserId(): string | null {
  if (typeof localStorage === 'undefined') return null
  return localStorage.getItem(AUTH_USER_ID_KEY)
}

/**
 * 获取认证头（用于真实 API 请求）
 *
 * @throws AuthRequiredError 若未登录
 * @returns { Authorization: 'Bearer <token>' }
 */
export function getAuthHeaders(): HeadersInit {
  const token = getStoredToken()

  if (!token) {
    throw new AuthRequiredError()
  }

  return {
    Authorization: `Bearer ${token}`,
  }
}

/**
 * 获取带 Content-Type 的认证头（用于 POST/PATCH/PUT 请求）
 *
 * @throws AuthRequiredError 若未登录
 * @returns { Authorization: 'Bearer <token>', 'Content-Type': 'application/json' }
 */
export function getAuthHeadersWithContentType(): HeadersInit {
  return {
    ...getAuthHeaders(),
    'Content-Type': 'application/json',
  }
}

/**
 * 检查是否已登录
 */
export function isLoggedIn(): boolean {
  return Boolean(getStoredToken())
}

/**
 * 获取当前用户 ID（如有）
 */
export function getCurrentUserId(): string | null {
  return getStoredUserId()
}
