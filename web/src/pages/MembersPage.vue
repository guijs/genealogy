<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { isUsingGraphApi } from '../features/tree/api/graphClient'
import { getCurrentUserId } from '../features/tree/api/auth'
import {
  listMembers,
  addMember,
  updateMemberRole,
  removeMember,
  MemberApiError,
  type FamilyMember,
  type MemberRole,
} from '../features/tree/api/memberClient'

const router = useRouter()
const route = useRoute()

const members = ref<FamilyMember[]>([])
const loading = ref(false)
const submitting = ref(false)
const updatingMember = ref<string | null>(null)
const removingMember = ref<string | null>(null)
const errorMessage = ref('')
const successMessage = ref('')

const addUserId = ref('')
const addRole = ref<MemberRole>('viewer')

const usingGraphApi = computed(() => isUsingGraphApi())
const familyId = computed(() => route.query.familyId as string | undefined)

const currentUserId = computed(() => {
  return getCurrentUserId() ?? ''
})

const isAdmin = computed(() => {
  if (!currentUserId.value) return false
  const currentMember = members.value.find((m) => m.user_id === currentUserId.value)
  return currentMember?.role === 'admin'
})

const canSubmit = computed(() => {
  const userId = addUserId.value.trim()
  if (!userId || submitting.value) return false
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
  return uuidRegex.test(userId)
})

const roleOptions: { value: MemberRole; label: string }[] = [
  { value: 'admin', label: '管理员 (admin)' },
  { value: 'editor', label: '编辑者 (editor)' },
  { value: 'viewer', label: '查看者 (viewer)' },
]

onMounted(() => {
  if (familyId.value && usingGraphApi.value) {
    void loadMembers()
  }
})

watch(
  () => route.query.familyId,
  (newFamilyId, oldFamilyId) => {
    if (newFamilyId !== oldFamilyId && newFamilyId && usingGraphApi.value) {
      void loadMembers()
    }
  },
)

async function loadMembers() {
  if (!familyId.value) return

  loading.value = true
  errorMessage.value = ''

  try {
    const result = await listMembers(familyId.value)
    members.value = result.members
  } catch (err) {
    if (err instanceof MemberApiError) {
      errorMessage.value = err.message
    } else {
      errorMessage.value = '加载成员列表失败'
    }
  } finally {
    loading.value = false
  }
}

async function handleAddMember() {
  if (!canSubmit.value || !familyId.value) return

  submitting.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const result = await addMember(familyId.value, {
      user_id: addUserId.value.trim(),
      role: addRole.value,
    })
    members.value = [...members.value, result]
    addUserId.value = ''
    addRole.value = 'viewer'
    successMessage.value = '成员添加成功'
    setTimeout(() => {
      successMessage.value = ''
    }, 3000)
  } catch (err) {
    if (err instanceof MemberApiError) {
      errorMessage.value = err.message
    } else {
      errorMessage.value = '添加成员失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

async function handleRoleChange(userId: string, newRole: MemberRole) {
  if (!familyId.value || updatingMember.value) return

  updatingMember.value = userId
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const result = await updateMemberRole(familyId.value, userId, newRole)
    members.value = members.value.map((m) =>
      m.user_id === userId ? { ...m, role: result.role } : m
    )
    successMessage.value = '角色更新成功'
    setTimeout(() => {
      successMessage.value = ''
    }, 3000)
  } catch (err) {
    if (err instanceof MemberApiError) {
      errorMessage.value = err.message
    } else {
      errorMessage.value = '更新角色失败，请稍后重试'
    }
  } finally {
    updatingMember.value = null
  }
}

async function handleRemoveMember(userId: string) {
  if (!familyId.value || removingMember.value) return

  const member = members.value.find((m) => m.user_id === userId)
  if (!member) return

  const confirmed = window.confirm(`确定要移除该成员吗？\n用户 ID: ${userId}`)
  if (!confirmed) return

  removingMember.value = userId
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await removeMember(familyId.value, userId)
    members.value = members.value.filter((m) => m.user_id !== userId)
    successMessage.value = '成员已移除'
    setTimeout(() => {
      successMessage.value = ''
    }, 3000)
  } catch (err) {
    if (err instanceof MemberApiError) {
      errorMessage.value = err.message
    } else {
      errorMessage.value = '移除成员失败，请稍后重试'
    }
  } finally {
    removingMember.value = null
  }
}

function goToTree() {
  if (familyId.value) {
    router.push({ name: 'tree', query: { familyId: familyId.value } })
  } else {
    router.push({ name: 'tree' })
  }
}

function goToCreateFamily() {
  router.push({ name: 'createFamily' })
}

function getRoleLabel(role: MemberRole): string {
  const option = roleOptions.find((o) => o.value === role)
  return option ? option.label : role
}
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div>
        <h1>家族成员管理</h1>
        <p class="sub">
          <template v-if="usingGraphApi">
            已接真 API ·
            <code>GET/POST /api/v1/families/{'{familyId}'}/members</code>
          </template>
          <template v-else>
            当前为 mock 模式 · 需要连接真实 API
          </template>
        </p>
      </div>
      <div class="topbar-right">
        <button type="button" class="btn-secondary" @click="goToTree">
          返回家族树
        </button>
        <button type="button" class="btn-secondary" @click="goToCreateFamily">
          家族管理
        </button>
      </div>
    </header>

    <main class="content">
      <div v-if="errorMessage" class="toast toast-error" role="alert">
        {{ errorMessage }}
      </div>
      <div v-if="successMessage" class="toast toast-success" role="status">
        {{ successMessage }}
      </div>

      <div v-if="!familyId" class="warning-card">
        <p class="warning-title">未指定家族</p>
        <p class="warning-desc">请先从家族树或家族列表中选择一个家族，或在 URL 中添加 ?familyId=xxx 参数</p>
        <div class="warning-actions">
          <button type="button" class="btn-primary" @click="goToCreateFamily">
            前往家族列表
          </button>
        </div>
      </div>

      <template v-else>
        <div class="info-bar">
          <span class="info-label">当前家族 ID：</span>
          <code class="info-value">{{ familyId }}</code>
        </div>

        <div class="members-card">
          <h2 class="card-title">成员列表</h2>

          <div v-if="!usingGraphApi" class="warning-banner">
            需要连接真实 API。请设置环境变量 VITE_USE_GRAPH_API=true 或 VITE_GRAPH_API_BASE。
          </div>

          <div v-else-if="loading" class="loading-text">加载中…</div>

          <div v-else-if="members.length === 0" class="empty-text">
            暂无成员
          </div>

          <ul v-else class="member-list">
            <li
              v-for="member in members"
              :key="member.user_id"
              class="member-item"
            >
              <div class="member-info">
                <span class="member-label">用户 ID</span>
                <code class="member-id">{{ member.user_id }}</code>
                <span v-if="member.user_id === currentUserId" class="member-badge">（当前用户）</span>
              </div>
              <div class="member-actions">
                <template v-if="isAdmin">
                  <select
                    class="role-select"
                    :value="member.role"
                    :disabled="updatingMember === member.user_id || removingMember === member.user_id"
                    @change="(e) => handleRoleChange(member.user_id, (e.target as HTMLSelectElement).value as MemberRole)"
                  >
                    <option
                      v-for="opt in roleOptions"
                      :key="opt.value"
                      :value="opt.value"
                    >
                      {{ opt.label }}
                    </option>
                  </select>
                  <button
                    type="button"
                    class="btn-danger-sm"
                    :disabled="removingMember === member.user_id || updatingMember === member.user_id"
                    @click="handleRemoveMember(member.user_id)"
                  >
                    {{ removingMember === member.user_id ? '移除中…' : '移除' }}
                  </button>
                </template>
                <span v-else class="member-role" :class="`role-${member.role}`">
                  {{ getRoleLabel(member.role) }}
                </span>
              </div>
            </li>
          </ul>
        </div>

        <div v-if="isAdmin" class="form-card">
          <h2 class="card-title">添加成员</h2>

          <div v-if="!usingGraphApi" class="warning-banner">
            需要连接真实 API。请设置环境变量 VITE_USE_GRAPH_API=true 或 VITE_GRAPH_API_BASE。
          </div>

          <form v-else @submit.prevent="handleAddMember">
            <div class="form-row">
              <label class="form-label" for="userId">用户 ID (UUID)</label>
              <input
                id="userId"
                v-model="addUserId"
                type="text"
                class="form-input"
                placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                :disabled="submitting"
                autocomplete="off"
              />
              <p class="form-hint">输入要添加的用户 UUID</p>
            </div>

            <div class="form-row">
              <label class="form-label" for="role">角色</label>
              <select
                id="role"
                v-model="addRole"
                class="form-select"
                :disabled="submitting"
              >
                <option
                  v-for="opt in roleOptions"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </option>
              </select>
            </div>

            <div class="form-actions">
              <button
                type="submit"
                class="btn-primary"
                :disabled="!canSubmit"
              >
                {{ submitting ? '添加中…' : '添加成员' }}
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

.topbar-right {
  display: flex;
  gap: 10px;
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
  padding: 24px 20px;
  gap: 20px;
}

.info-bar {
  width: min(500px, 100%);
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--color-border, #d8d4cc);
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-label {
  font-size: 14px;
  color: #666;
}

.info-value {
  font-size: 13px;
  background: #f3f1ec;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: monospace;
}

.members-card,
.form-card,
.warning-card {
  width: min(500px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.card-title {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
}

.warning-card {
  text-align: center;
}

.warning-title {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 600;
  color: #8a6d3b;
}

.warning-desc {
  margin: 0 0 20px;
  font-size: 14px;
  color: #666;
}

.warning-actions {
  display: flex;
  justify-content: center;
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

.loading-text,
.empty-text {
  font-size: 14px;
  color: #666;
  text-align: center;
  padding: 20px 0;
}

.member-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.member-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 12px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 8px;
  margin-bottom: 10px;
}

.member-item:last-child {
  margin-bottom: 0;
}

.member-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.member-badge {
  font-size: 12px;
  color: #666;
  font-style: italic;
}

.member-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-select {
  padding: 6px 10px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  background: #fff;
  cursor: pointer;
}

.role-select:focus {
  outline: none;
  border-color: var(--color-accent, #2f5d50);
}

.role-select:disabled {
  background: #f0eeeb;
  cursor: not-allowed;
}

.btn-danger-sm {
  padding: 6px 12px;
  border: 1px solid #c53030;
  border-radius: 6px;
  background: #fff;
  color: #c53030;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.btn-danger-sm:hover:not(:disabled) {
  background: #c53030;
  color: #fff;
}

.btn-danger-sm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.member-label {
  font-size: 12px;
  color: #888;
}

.member-id {
  font-size: 13px;
  font-family: monospace;
  background: #f3f1ec;
  padding: 2px 6px;
  border-radius: 4px;
}

.member-role {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.role-admin {
  background: #fef0f0;
  color: #c53030;
}

.role-editor {
  background: #fff4e5;
  color: #8a6d3b;
}

.role-viewer {
  background: #f0fff4;
  color: #276749;
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

.form-input,
.form-select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  font-size: 15px;
  font-family: inherit;
  box-sizing: border-box;
  background: #fff;
}

.form-input:focus,
.form-select:focus {
  outline: none;
  border-color: var(--color-accent, #2f5d50);
}

.form-input:disabled,
.form-select:disabled {
  background: #f0eeeb;
  cursor: not-allowed;
}

.form-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #888;
}

.form-actions {
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
  padding: 8px 14px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: #fff;
  font-size: 14px;
  cursor: pointer;
}

.btn-secondary:hover {
  background: #f5f5f5;
}

.toast {
  width: min(500px, 100%);
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

.toast-success {
  background: #f0fff4;
  color: #276749;
  border: 1px solid #9ae6b4;
}
</style>
