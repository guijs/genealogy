<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter, useRoute } from 'vue-router'
import TreeCanvas from '../features/tree/canvas/TreeCanvas.vue'
import PersonDetailDrawer from '../features/tree/panels/PersonDetailDrawer.vue'
import { useTreeViewStore } from '../features/tree/state/treeViewStore'
import { useAuthStore } from '../stores/authStore'

const router = useRouter()
const route = useRoute()
const store = useTreeViewStore()
const authStore = useAuthStore()
const {
  loading,
  truncated,
  truncateReason,
  graph,
  usingGraphApi,
  addFormOpen,
  addSpouseFormOpen,
  endMarriageFormOpen,
  endMarriageId,
  emptyFamily,
  selectedPerson,
  submitting,
  errorMessage,
  successMessage,
  hideConfirmOpen,
  hideConfirmMessage,
  hiddenPersonsForCurrentFamily,
} = storeToRefs(store)

const hiddenPanelOpen = ref(false)

function toggleHiddenPanel() {
  hiddenPanelOpen.value = !hiddenPanelOpen.value
}

async function handleRestoreFromPanel(personId: string) {
  await store.restorePersonFromPanel(personId)
}

const addFirstName = ref('')
const addLastName = ref('')
const selectedSpouseId = ref('')
const spouseStartedAt = ref('')
const endMarriageReason = ref('')
const endMarriageDate = ref('')

onMounted(() => {
  const familyId = route.query.familyId as string | undefined
  const rootPersonId = route.query.rootPersonId as string | undefined

  if (familyId) {
    void store.loadFamily(familyId, rootPersonId)
  } else {
    void store.loadDemo()
  }
})

// 监听路由参数变化（例如从家族列表点击不同家族）
watch(
  () => route.query.familyId,
  (newFamilyId, oldFamilyId) => {
    if (newFamilyId !== oldFamilyId) {
      const familyId = newFamilyId as string | undefined
      const rootPersonId = route.query.rootPersonId as string | undefined

      if (familyId) {
        void store.loadFamily(familyId, rootPersonId)
      } else {
        void store.loadDemo()
      }
    }
  },
)

const availableSpouseCandidates = computed(() => {
  if (!graph.value || !selectedPerson.value) return []
  return graph.value.persons.filter(
    (p) => p.id !== selectedPerson.value?.id,
  )
})

function openAddForm() {
  addFirstName.value = ''
  addLastName.value = ''
  store.openAddForm()
}

async function handleAddPerson() {
  const firstName = addFirstName.value.trim()
  const lastName = addLastName.value.trim()
  if (!firstName || !lastName) return

  const ok = await store.createPerson({
    first_name: firstName,
    last_name: lastName,
  })
  if (ok) {
    addFirstName.value = ''
    addLastName.value = ''
  }
}

function handleCancelAdd() {
  store.closeAddForm()
}

function goToCreateFamily() {
  router.push({ name: 'createFamily' })
}

function goToMembers() {
  const familyId = route.query.familyId as string | undefined
  if (familyId) {
    router.push({ name: 'members', query: { familyId } })
  } else {
    router.push({ name: 'members' })
  }
}

function goToMediaUpload() {
  const familyId = route.query.familyId as string | undefined
  if (familyId) {
    router.push({ name: 'mediaUpload', query: { familyId } })
  } else {
    router.push({ name: 'mediaUpload' })
  }
}

watch(addSpouseFormOpen, (open) => {
  if (open) {
    selectedSpouseId.value = ''
    spouseStartedAt.value = ''
  }
})

watch(endMarriageFormOpen, (open) => {
  if (open) {
    endMarriageReason.value = ''
    endMarriageDate.value = ''
  }
})

async function handleAddSpouse() {
  const partnerId = selectedSpouseId.value.trim()
  if (!partnerId) return

  const ok = await store.addSpouse(
    partnerId,
    spouseStartedAt.value.trim() || null,
  )
  if (ok) {
    selectedSpouseId.value = ''
    spouseStartedAt.value = ''
  }
}

function handleCancelAddSpouse() {
  store.closeAddSpouseForm()
}

async function handleEndMarriage() {
  if (!endMarriageId.value) return

  const ok = await store.endMarriage(
    endMarriageId.value,
    endMarriageReason.value.trim() || null,
    endMarriageDate.value.trim() || null,
  )
  if (ok) {
    endMarriageReason.value = ''
    endMarriageDate.value = ''
  }
}

function handleCancelEndMarriage() {
  store.closeEndMarriageForm()
}

async function handleConfirmHide() {
  await store.confirmHidePerson()
}

function handleCancelHideConfirm() {
  store.cancelHideConfirm()
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
        <h1>家族网络（只读投影）</h1>
        <p class="sub">
          <template v-if="usingGraphApi">
            已接真 API ·
            <code>GET /api/v1/families/{'{familyId}'}/graph</code>
          </template>
          <template v-else>
            当前使用 fixture mock · 对接未来
            <code>GET /api/v1/families/{'{familyId}'}/graph</code>
          </template>
        </p>
      </div>
      <div class="topbar-right">
        <button
          type="button"
          class="btn-create-family"
          @click="goToCreateFamily"
        >
          创建家族
        </button>
        <button
          v-if="usingGraphApi"
          type="button"
          class="btn-create-family"
          @click="goToMembers"
        >
          成员管理
        </button>
        <button
          v-if="usingGraphApi"
          type="button"
          class="btn-create-family"
          @click="goToMediaUpload"
        >
          媒体上传
        </button>
        <button
          v-if="usingGraphApi && hiddenPersonsForCurrentFamily.length > 0"
          type="button"
          class="btn-hidden-list"
          @click="toggleHiddenPanel"
        >
          已隐藏 ({{ hiddenPersonsForCurrentFamily.length }})
        </button>
        <button
          v-if="usingGraphApi"
          type="button"
          class="btn-add"
          @click="openAddForm"
        >
          添加成员
        </button>
        <button
          v-if="authStore.isAuthenticated && usingGraphApi"
          type="button"
          class="btn-logout"
          @click="handleLogout"
        >
          登出
        </button>
        <div v-if="graph" class="meta">
          焦点：{{ graph.rootPersonId }} · depth={{ graph.depth }}
          <span class="hint">（up1+down2+焦点 ⇒ depth=3）</span>
        </div>
      </div>
    </header>

    <!-- 消息提示 -->
    <div v-if="errorMessage" class="toast toast-error" role="alert">
      {{ errorMessage }}
    </div>
    <div v-if="successMessage" class="toast toast-success" role="status">
      {{ successMessage }}
    </div>

    <div v-if="truncated" class="banner" role="status">
      {{ truncateReason || '已达展开上限' }}
      <span class="banner-hint">可换焦点或收起旁支（P0 示意）</span>
    </div>

    <!-- 添加成员表单（模态） -->
    <div v-if="addFormOpen" class="modal-overlay" @click.self="handleCancelAdd">
      <div class="modal-box">
        <h2 class="modal-title">添加成员</h2>
        <div class="form-row">
          <label class="form-label">姓</label>
          <input
            v-model="addLastName"
            type="text"
            class="form-input"
            placeholder="姓氏"
            :disabled="submitting"
          />
        </div>
        <div class="form-row">
          <label class="form-label">名</label>
          <input
            v-model="addFirstName"
            type="text"
            class="form-input"
            placeholder="名字"
            :disabled="submitting"
          />
        </div>
        <p class="form-note">
          仅添加基础信息，亲属关系请后续编辑
        </p>
        <div class="form-actions">
          <button
            type="button"
            class="btn-submit"
            :disabled="submitting || !addFirstName.trim() || !addLastName.trim()"
            @click="handleAddPerson"
          >
            {{ submitting ? '添加中…' : '添加' }}
          </button>
          <button
            type="button"
            class="btn-close"
            :disabled="submitting"
            @click="handleCancelAdd"
          >
            取消
          </button>
        </div>
      </div>
    </div>

    <!-- 添加配偶表单（模态） -->
    <div v-if="addSpouseFormOpen" class="modal-overlay" @click.self="handleCancelAddSpouse">
      <div class="modal-box">
        <h2 class="modal-title">添加配偶</h2>
        <p class="modal-desc">
          为 <strong>{{ selectedPerson?.displayName }}</strong> 添加配偶
        </p>
        <div class="form-row">
          <label class="form-label">选择配偶</label>
          <select
            v-model="selectedSpouseId"
            class="form-select"
            :disabled="submitting"
          >
            <option value="">请选择...</option>
            <option
              v-for="p in availableSpouseCandidates"
              :key="p.id"
              :value="p.id"
            >
              {{ p.displayName }}
            </option>
          </select>
        </div>
        <div class="form-row">
          <label class="form-label">结婚日期（可选）</label>
          <input
            v-model="spouseStartedAt"
            type="text"
            class="form-input"
            placeholder="如：1990 或 1990-06-15"
            :disabled="submitting"
          />
        </div>
        <p class="form-note">
          添加配偶后将创建婚姻关系
        </p>
        <div class="form-actions">
          <button
            type="button"
            class="btn-submit"
            :disabled="submitting || !selectedSpouseId"
            @click="handleAddSpouse"
          >
            {{ submitting ? '添加中…' : '添加' }}
          </button>
          <button
            type="button"
            class="btn-close"
            :disabled="submitting"
            @click="handleCancelAddSpouse"
          >
            取消
          </button>
        </div>
      </div>
    </div>

    <!-- 结束婚姻表单（模态） -->
    <div v-if="endMarriageFormOpen" class="modal-overlay" @click.self="handleCancelEndMarriage">
      <div class="modal-box">
        <h2 class="modal-title">结束婚姻</h2>
        <p class="modal-desc">
          确认要结束此婚姻关系吗？这不会删除配偶信息。
        </p>
        <div class="form-row">
          <label class="form-label">结束原因（可选）</label>
          <select
            v-model="endMarriageReason"
            class="form-select"
            :disabled="submitting"
          >
            <option value="">请选择...</option>
            <option value="divorced">离婚</option>
            <option value="widowed">丧偶</option>
            <option value="other">其他</option>
          </select>
        </div>
        <div class="form-row">
          <label class="form-label">结束日期（可选）</label>
          <input
            v-model="endMarriageDate"
            type="text"
            class="form-input"
            placeholder="如：2020 或 2020-01-15"
            :disabled="submitting"
          />
        </div>
        <div class="form-actions">
          <button
            type="button"
            class="btn-submit btn-danger"
            :disabled="submitting"
            @click="handleEndMarriage"
          >
            {{ submitting ? '处理中…' : '确认结束' }}
          </button>
          <button
            type="button"
            class="btn-close"
            :disabled="submitting"
            @click="handleCancelEndMarriage"
          >
            取消
          </button>
        </div>
      </div>
    </div>

    <!-- 隐藏成员确认（活跃婚姻冲突 409） -->
    <div v-if="hideConfirmOpen" class="modal-overlay" @click.self="handleCancelHideConfirm">
      <div class="modal-box">
        <h2 class="modal-title">确认隐藏</h2>
        <p class="modal-desc modal-warn">
          {{ hideConfirmMessage }}
        </p>
        <p class="modal-hint">
          隐藏此成员后，其相关的婚姻关系也将从家族树中消失。确定要继续吗？
        </p>
        <div class="form-actions">
          <button
            type="button"
            class="btn-submit btn-danger"
            :disabled="submitting"
            @click="handleConfirmHide"
          >
            {{ submitting ? '处理中…' : '确认隐藏' }}
          </button>
          <button
            type="button"
            class="btn-close"
            :disabled="submitting"
            @click="handleCancelHideConfirm"
          >
            取消
          </button>
        </div>
      </div>
    </div>

    <div class="stage">
      <p v-if="loading" class="loading">加载投影…</p>
      <div v-else-if="emptyFamily" class="empty-state">
        <p class="empty-title">这是一个新家族</p>
        <p class="empty-desc">暂无成员，请先添加第一位家族成员</p>
        <button
          v-if="usingGraphApi"
          type="button"
          class="btn-add-first"
          @click="openAddForm"
        >
          添加第一位成员
        </button>
      </div>
      <TreeCanvas v-else />
      <PersonDetailDrawer />

      <!-- 已隐藏成员面板 -->
      <aside
        v-if="hiddenPanelOpen && hiddenPersonsForCurrentFamily.length > 0"
        class="hidden-panel"
        aria-label="已隐藏成员"
      >
        <header class="hidden-panel-head">
          <h3 class="hidden-panel-title">已隐藏</h3>
          <button type="button" class="hidden-panel-close" @click="toggleHiddenPanel">
            ✕
          </button>
        </header>
        <p class="hidden-panel-desc">
          以下成员已从家族树隐藏，点击「恢复」可重新显示。
        </p>
        <ul class="hidden-list">
          <li
            v-for="person in hiddenPersonsForCurrentFamily"
            :key="person.id"
            class="hidden-item"
          >
            <span class="hidden-name">{{ person.displayName }}</span>
            <button
              type="button"
              class="btn-restore-small"
              :disabled="submitting"
              @click="handleRestoreFromPanel(person.id)"
            >
              {{ submitting ? '…' : '恢复' }}
            </button>
          </li>
        </ul>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
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
  align-items: flex-end;
  gap: 16px;
}
.btn-add,
.btn-create-family {
  padding: 6px 14px;
  border: 1px solid var(--color-accent, #2f5d50);
  border-radius: 6px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}
.btn-create-family {
  background: #fff;
  color: var(--color-accent, #2f5d50);
}
.btn-add:hover,
.btn-create-family:hover {
  opacity: 0.9;
}
.btn-logout {
  padding: 6px 14px;
  border: 1px solid #c53030;
  border-radius: 6px;
  background: #fff;
  color: #c53030;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}
.btn-logout:hover {
  background: #fef0f0;
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
.meta {
  font-size: 13px;
  color: #555;
}
.hint {
  color: #888;
}
.banner {
  padding: 10px 20px;
  background: var(--color-banner, #fff4e5);
  border-bottom: 1px solid var(--color-banner-border, #e6a23c);
  font-size: 15px;
  font-weight: 500;
}
.banner-hint {
  margin-left: 12px;
  font-weight: 400;
  font-size: 13px;
  color: #8a6d3b;
}
.stage {
  position: relative;
  flex: 1;
  min-height: 0;
}
.loading {
  padding: 24px;
}
/* 消息提示 */
.toast {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 20px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
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
/* 模态框 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}
.modal-box {
  width: min(400px, 90%);
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.modal-title {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
}
.form-row {
  margin-bottom: 14px;
}
.form-label {
  display: block;
  font-size: 15px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
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
.form-note {
  margin: 0 0 16px;
  font-size: 13px;
  color: #666;
}
.form-actions {
  display: flex;
  gap: 10px;
}
.btn-submit {
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
.btn-submit:hover:not(:disabled) {
  opacity: 0.9;
}
.btn-submit:disabled {
  background: #ccc;
  cursor: not-allowed;
}
.btn-close {
  padding: 10px 16px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: #fff;
  font-size: 15px;
  cursor: pointer;
}
.btn-close:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
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
.form-select:focus {
  outline: none;
  border-color: var(--color-accent, #2f5d50);
}
.form-select:disabled {
  background: #f0eeeb;
  cursor: not-allowed;
}
.modal-desc {
  margin: 0 0 16px;
  font-size: 14px;
  color: #555;
}
.modal-desc.modal-warn {
  padding: 12px;
  background: #fff4e5;
  border-radius: 6px;
  color: #8a6d3b;
}
.modal-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: #666;
}
.btn-danger {
  background: #c53030;
}
.btn-danger:hover:not(:disabled) {
  background: #9b2c2c;
}
/* 空家族状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
  text-align: center;
}
.empty-title {
  margin: 0 0 12px;
  font-size: 20px;
  font-weight: 600;
  color: #333;
}
.empty-desc {
  margin: 0 0 24px;
  font-size: 15px;
  color: #666;
}
.btn-add-first {
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
}
.btn-add-first:hover {
  opacity: 0.9;
}
/* 已隐藏成员按钮 */
.btn-hidden-list {
  padding: 6px 14px;
  border: 1px solid #8a6d3b;
  border-radius: 6px;
  background: #fff4e5;
  color: #8a6d3b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}
.btn-hidden-list:hover {
  background: #ffeccc;
}
/* 已隐藏成员面板 */
.hidden-panel {
  position: absolute;
  top: 0;
  left: 0;
  width: min(300px, 100%);
  max-height: 100%;
  background: var(--color-card, #fff);
  border-right: 1px solid var(--color-border, #d8d4cc);
  box-shadow: 4px 0 16px rgba(0, 0, 0, 0.06);
  padding: 16px;
  overflow: auto;
  z-index: 15;
  box-sizing: border-box;
}
.hidden-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.hidden-panel-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #8a6d3b;
}
.hidden-panel-close {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  color: #666;
  padding: 4px 8px;
}
.hidden-panel-close:hover {
  color: #333;
}
.hidden-panel-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: #666;
}
.hidden-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.hidden-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: #faf9f7;
  border-radius: 6px;
  margin-bottom: 8px;
}
.hidden-item:last-child {
  margin-bottom: 0;
}
.hidden-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.btn-restore-small {
  flex-shrink: 0;
  padding: 4px 12px;
  border: none;
  border-radius: 4px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.btn-restore-small:hover:not(:disabled) {
  opacity: 0.9;
}
.btn-restore-small:disabled {
  background: #ccc;
  cursor: not-allowed;
}
</style>
