<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { isUsingGraphApi } from '../features/tree/api/graphClient'
import {
  createFamily,
  listFamilies,
  FamilyApiError,
  type FamilyResponse,
} from '../features/tree/api/familyClient'
import { useAuthStore } from '../stores/authStore'

const router = useRouter()
const authStore = useAuthStore()

const familyName = ref('')
const submitting = ref(false)
const loadingList = ref(false)
const errorMessage = ref('')
const createdFamily = ref<FamilyResponse | null>(null)
const families = ref<FamilyResponse[]>([])

const usingGraphApi = computed(() => isUsingGraphApi())
const canSubmit = computed(() => familyName.value.trim().length > 0 && !submitting.value)

onMounted(() => {
  if (usingGraphApi.value) {
    void loadFamilies()
  }
})

async function loadFamilies() {
  loadingList.value = true
  errorMessage.value = ''
  try {
    const result = await listFamilies()
    families.value = result.families
  } catch (err) {
    if (err instanceof FamilyApiError) {
      errorMessage.value = err.message
    }
  } finally {
    loadingList.value = false
  }
}

async function handleCreate() {
  if (!canSubmit.value) return

  submitting.value = true
  errorMessage.value = ''
  createdFamily.value = null

  try {
    const result = await createFamily({ name: familyName.value.trim() })
    createdFamily.value = result
    familyName.value = ''
    families.value = [result, ...families.value]
  } catch (err) {
    if (err instanceof FamilyApiError) {
      errorMessage.value = err.message
    } else {
      errorMessage.value = '创建失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

function navigateToTree() {
  if (createdFamily.value) {
    router.push({ name: 'tree', query: { familyId: createdFamily.value.id } })
  }
}

function navigateToFamilyTree(familyId: string) {
  router.push({ name: 'tree', query: { familyId } })
}

function goToTree() {
  router.push({ name: 'tree' })
}

function handleLogout() {
  authStore.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div>
        <h1>家族管理</h1>
        <p class="sub">
          <template v-if="usingGraphApi">
            已接真 API ·
            <code>GET/POST /api/v1/families</code>
          </template>
          <template v-else>
            当前为 mock 模式 · 需要连接真实 API
          </template>
        </p>
      </div>
      <div class="topbar-actions">
        <button type="button" class="btn-secondary" @click="goToTree">
          返回家族树
        </button>
        <button
          v-if="authStore.isAuthenticated && usingGraphApi"
          type="button"
          class="btn-logout"
          @click="handleLogout"
        >
          登出
        </button>
      </div>
    </header>

    <main class="content">
      <div v-if="errorMessage" class="toast toast-error" role="alert">
        {{ errorMessage }}
      </div>

      <div v-if="createdFamily" class="success-card">
        <h2 class="success-title">家族创建成功！</h2>
        <div class="success-info">
          <div class="info-row">
            <span class="info-label">家族 ID：</span>
            <code class="info-value">{{ createdFamily.id }}</code>
          </div>
          <div class="info-row">
            <span class="info-label">家族名称：</span>
            <span class="info-value">{{ createdFamily.name }}</span>
          </div>
        </div>
        <div class="success-actions">
          <button type="button" class="btn-primary" @click="navigateToTree">
            前往家族树
          </button>
          <button type="button" class="btn-secondary" @click="createdFamily = null">
            继续创建
          </button>
        </div>
      </div>

      <template v-else>
        <!-- 家族列表 -->
        <div v-if="usingGraphApi" class="list-card">
          <h2 class="list-title">我的家族</h2>
          <div v-if="loadingList" class="loading-text">加载中…</div>
          <div v-else-if="families.length === 0" class="empty-text">
            暂无家族，请创建第一个家族
          </div>
          <ul v-else class="family-list">
            <li
              v-for="family in families"
              :key="family.id"
              class="family-item"
              @click="navigateToFamilyTree(family.id)"
            >
              <span class="family-name">{{ family.name }}</span>
              <code class="family-id">{{ family.id }}</code>
            </li>
          </ul>
        </div>

        <!-- 创建表单 -->
        <div class="form-card">
          <h2 class="form-title">新建家族</h2>

          <div v-if="!usingGraphApi" class="warning-banner">
            需要连接真实 API。请设置环境变量 VITE_USE_GRAPH_API=true 或 VITE_GRAPH_API_BASE。
          </div>

          <form @submit.prevent="handleCreate">
            <div class="form-row">
              <label class="form-label" for="familyName">家族名称</label>
              <input
                id="familyName"
                v-model="familyName"
                type="text"
                class="form-input"
                placeholder="输入家族名称"
                :disabled="submitting || !usingGraphApi"
                autocomplete="off"
              />
            </div>

            <div class="form-actions">
              <button
                type="submit"
                class="btn-primary"
                :disabled="!canSubmit || !usingGraphApi"
              >
                {{ submitting ? '创建中…' : '创建家族' }}
              </button>
            </div>
          </form>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  font-family: var(--font-cn, sans-serif);
  background: var(--color-paper, #faf9f7);
  color: var(--color-ink, #1f1f1f);
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-border, #d8d4cc);
  background: #fff;
}

.topbar h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #666;
}

.sub code {
  font-size: 12px;
  background: #f3f1ec;
  padding: 1px 4px;
  border-radius: 4px;
}

.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
}

.form-card,
.success-card,
.list-card {
  width: min(400px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.list-card {
  margin-bottom: 20px;
}

.form-title,
.success-title,
.list-title {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
}

.success-title {
  color: #276749;
}

.loading-text,
.empty-text {
  font-size: 14px;
  color: #666;
  text-align: center;
  padding: 12px 0;
}

.family-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.family-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: background-color 0.15s;
}

.family-item:hover {
  background: #f5f5f5;
}

.family-item:last-child {
  margin-bottom: 0;
}

.family-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
}

.family-id {
  font-size: 12px;
  color: #888;
  background: #f3f1ec;
  padding: 2px 6px;
  border-radius: 4px;
}

.warning-banner {
  padding: 12px;
  margin-bottom: 16px;
  background: #fff4e5;
  border: 1px solid #e6a23c;
  border-radius: 6px;
  font-size: 13px;
  color: #8a6d3b;
}

.form-row {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  font-size: 15px;
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
}

.form-input:focus {
  outline: none;
  border-color: var(--color-accent, #2f5d50);
}

.form-input:disabled {
  background: #f0eeeb;
  cursor: not-allowed;
}

.form-actions,
.success-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.btn-primary {
  flex: 1;
  padding: 10px 16px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 10px 16px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: #fff;
  font-size: 15px;
  cursor: pointer;
}

.btn-secondary:hover {
  background: #f5f5f5;
}

.topbar-actions {
  display: flex;
  gap: 8px;
}

.btn-logout {
  padding: 10px 16px;
  border: 1px solid #c53030;
  border-radius: 6px;
  background: #fff;
  color: #c53030;
  font-size: 15px;
  cursor: pointer;
}

.btn-logout:hover {
  background: #fef0f0;
}

.toast {
  width: min(400px, 100%);
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

.success-info {
  margin-bottom: 20px;
}

.info-row {
  padding: 8px 0;
  border-bottom: 1px solid #eee;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 14px;
  color: #666;
}

.info-value {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

code.info-value {
  background: #f3f1ec;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}
</style>
