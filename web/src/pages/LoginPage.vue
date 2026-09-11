<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore, AuthError } from '../stores/authStore'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const errorMessage = ref('')

const isLoading = computed(() => authStore.loading)

const isValidEmail = computed(() => {
  if (!email.value) return true
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)
})

const canSubmit = computed(() => {
  return (
    email.value.trim().length > 0 &&
    password.value.length > 0 &&
    isValidEmail.value &&
    !isLoading.value
  )
})

async function handleLogin() {
  if (!canSubmit.value) return

  errorMessage.value = ''

  try {
    await authStore.login({
      email: email.value.trim(),
      password: password.value,
    })

    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (err) {
    if (err instanceof AuthError) {
      errorMessage.value = err.message
    } else {
      errorMessage.value = '登录失败，请稍后重试'
    }
  }
}

function goToRegister() {
  const redirect = route.query.redirect
  router.push({
    name: 'register',
    query: redirect ? { redirect } : undefined,
  })
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">登录</h1>
      <p class="auth-subtitle">登录您的家族树账号</p>

      <div v-if="errorMessage" class="toast toast-error" role="alert">
        {{ errorMessage }}
      </div>

      <form @submit.prevent="handleLogin">
        <div class="form-row">
          <label class="form-label" for="email">邮箱</label>
          <input
            id="email"
            v-model="email"
            type="email"
            class="form-input"
            :class="{ 'input-error': email && !isValidEmail }"
            placeholder="请输入邮箱"
            autocomplete="email"
            :disabled="isLoading"
          />
          <p v-if="email && !isValidEmail" class="field-error">
            请输入有效的邮箱地址
          </p>
        </div>

        <div class="form-row">
          <label class="form-label" for="password">密码</label>
          <input
            id="password"
            v-model="password"
            type="password"
            class="form-input"
            placeholder="请输入密码"
            autocomplete="current-password"
            :disabled="isLoading"
          />
        </div>

        <div class="form-actions">
          <button type="submit" class="btn-primary" :disabled="!canSubmit">
            {{ isLoading ? '登录中…' : '登录' }}
          </button>
        </div>
      </form>

      <div class="auth-footer">
        <span>还没有账号？</span>
        <button type="button" class="link-btn" @click="goToRegister">
          立即注册
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 20px;
  font-family: var(--font-cn, sans-serif);
  background: var(--color-paper, #faf9f7);
  color: var(--color-ink, #1f1f1f);
}

.auth-card {
  width: min(400px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.auth-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
}

.auth-subtitle {
  margin: 0 0 24px;
  font-size: 14px;
  color: #666;
  text-align: center;
}

.toast {
  margin-bottom: 20px;
  padding: 12px 16px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
}

.toast-error {
  background: #fef0f0;
  color: #c53030;
  border: 1px solid #f5c6cb;
}

.form-row {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  font-size: 15px;
  font-family: inherit;
  box-sizing: border-box;
  transition: border-color 0.15s;
}

.form-input:focus {
  outline: none;
  border-color: var(--color-accent, #2f5d50);
}

.form-input:disabled {
  background: #f0eeeb;
  cursor: not-allowed;
}

.input-error {
  border-color: #c53030;
}

.input-error:focus {
  border-color: #c53030;
}

.field-error {
  margin: 4px 0 0;
  font-size: 12px;
  color: #c53030;
}

.form-actions {
  margin-top: 24px;
}

.btn-primary {
  width: 100%;
  padding: 12px 16px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.auth-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 14px;
  color: #666;
}

.link-btn {
  padding: 0;
  border: none;
  background: none;
  color: var(--color-accent, #2f5d50);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  text-decoration: underline;
}

.link-btn:hover {
  opacity: 0.8;
}
</style>
