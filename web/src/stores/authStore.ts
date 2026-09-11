/**
 * Auth Store - JWT 认证状态管理
 *
 * 功能：
 * - 登录/注册/登出
 * - localStorage 持久化 token + user_id
 * - 提供 isAuthenticated 计算属性
 * - 错误处理（409 邮箱已注册、401 凭证无效）
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const AUTH_TOKEN_KEY = 'auth_token'
const AUTH_USER_ID_KEY = 'auth_user_id'

export interface AuthResponse {
  user_id: string
  token: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
}

export type AuthErrorCode =
  | 'INVALID_CREDENTIALS'
  | 'EMAIL_TAKEN'
  | 'VALIDATION_ERROR'
  | 'NETWORK_ERROR'
  | 'UNKNOWN'

export class AuthError extends Error {
  readonly status: number
  readonly code: AuthErrorCode

  constructor(message: string, status: number, code: AuthErrorCode) {
    super(message)
    this.name = 'AuthError'
    this.status = status
    this.code = code
  }
}

function apiBase(): string {
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return base ? base.replace(/\/$/, '') : ''
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(AUTH_TOKEN_KEY))
  const userId = ref<string | null>(localStorage.getItem(AUTH_USER_ID_KEY))
  const loading = ref(false)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(() => Boolean(token.value))

  function setAuth(response: AuthResponse) {
    token.value = response.token
    userId.value = response.user_id
    localStorage.setItem(AUTH_TOKEN_KEY, response.token)
    localStorage.setItem(AUTH_USER_ID_KEY, response.user_id)
    error.value = null
  }

  function clearAuth() {
    token.value = null
    userId.value = null
    localStorage.removeItem(AUTH_TOKEN_KEY)
    localStorage.removeItem(AUTH_USER_ID_KEY)
    error.value = null
  }

  async function login(data: LoginRequest): Promise<void> {
    loading.value = true
    error.value = null

    try {
      const base = apiBase()
      const url = `${base}/api/v1/auth/login`

      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      })

      if (!res.ok) {
        if (res.status === 401) {
          throw new AuthError('邮箱或密码错误', 401, 'INVALID_CREDENTIALS')
        }
        if (res.status === 400) {
          throw new AuthError('请输入有效的邮箱和密码', 400, 'VALIDATION_ERROR')
        }
        throw new AuthError(`登录失败: ${res.status}`, res.status, 'UNKNOWN')
      }

      const response = (await res.json()) as AuthResponse
      setAuth(response)
    } catch (err) {
      if (err instanceof AuthError) {
        error.value = err.message
        throw err
      }
      if (err instanceof TypeError) {
        error.value = '网络连接失败，请检查网络后重试'
        throw new AuthError('网络连接失败', 0, 'NETWORK_ERROR')
      }
      error.value = '登录失败，请稍后重试'
      throw new AuthError('登录失败', 0, 'UNKNOWN')
    } finally {
      loading.value = false
    }
  }

  async function register(data: RegisterRequest): Promise<void> {
    loading.value = true
    error.value = null

    try {
      const base = apiBase()
      const url = `${base}/api/v1/auth/register`

      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      })

      if (!res.ok) {
        if (res.status === 409) {
          throw new AuthError('该邮箱已被注册', 409, 'EMAIL_TAKEN')
        }
        if (res.status === 400) {
          throw new AuthError('请输入有效的邮箱和密码（密码至少8位）', 400, 'VALIDATION_ERROR')
        }
        throw new AuthError(`注册失败: ${res.status}`, res.status, 'UNKNOWN')
      }

      const response = (await res.json()) as AuthResponse
      setAuth(response)
    } catch (err) {
      if (err instanceof AuthError) {
        error.value = err.message
        throw err
      }
      if (err instanceof TypeError) {
        error.value = '网络连接失败，请检查网络后重试'
        throw new AuthError('网络连接失败', 0, 'NETWORK_ERROR')
      }
      error.value = '注册失败，请稍后重试'
      throw new AuthError('注册失败', 0, 'UNKNOWN')
    } finally {
      loading.value = false
    }
  }

  function logout() {
    clearAuth()
  }

  function getToken(): string | null {
    return token.value
  }

  function getUserId(): string | null {
    return userId.value
  }

  return {
    token,
    userId,
    loading,
    error,
    isAuthenticated,
    login,
    register,
    logout,
    getToken,
    getUserId,
  }
})
