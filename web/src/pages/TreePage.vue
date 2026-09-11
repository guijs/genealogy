<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import TreeCanvas from '../features/tree/canvas/TreeCanvas.vue'
import PersonDetailDrawer from '../features/tree/panels/PersonDetailDrawer.vue'
import { useTreeViewStore } from '../features/tree/state/treeViewStore'

const router = useRouter()
const store = useTreeViewStore()
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
  selectedPerson,
  submitting,
  errorMessage,
  successMessage,
} = storeToRefs(store)

const addFirstName = ref('')
const addLastName = ref('')
const selectedSpouseId = ref('')
const spouseStartedAt = ref('')
const endMarriageReason = ref('')
const endMarriageDate = ref('')

onMounted(() => {
  void store.loadDemo()
})

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
          class="btn-add"
          @click="openAddForm"
        >
          添加成员
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

    <div class="stage">
      <p v-if="loading" class="loading">加载投影…</p>
      <TreeCanvas v-else />
      <PersonDetailDrawer />
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
.btn-danger {
  background: #c53030;
}
.btn-danger:hover:not(:disabled) {
  background: #9b2c2c;
}
</style>
