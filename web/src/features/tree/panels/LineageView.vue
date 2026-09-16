<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import type { LineageResponse, GenerationNameAlign } from '../api/types'
import { fetchLineage, setProgenitor, getLineageLabel, LineageApiError } from '../api/lineageClient'
import {
  fetchGenerationNames,
  updateGenerationNames,
  validateGenerationNames,
  GenerationNamesApiError,
} from '../api/generationNamesClient'
import { listMembers, type FamilyMember, MemberApiError } from '../api/memberClient'
import { listPersons as apiListPersons, type PersonResponse } from '../api/personClient'
import { getCurrentUserId } from '../api/auth'
import { useTreeViewStore } from '../state/treeViewStore'

const store = useTreeViewStore()
const { currentFamilyId, usingGraphApi } = storeToRefs(store)

const loading = ref(false)
const loadError = ref<string | null>(null)
const actionError = ref<string | null>(null)
const lineage = ref<LineageResponse | null>(null)

const members = ref<FamilyMember[]>([])
const persons = ref<PersonResponse[]>([])

const currentUserId = computed(() => getCurrentUserId() ?? '')

const isAdmin = computed(() => {
  if (!currentUserId.value) return false
  const currentMember = members.value.find((m) => m.user_id === currentUserId.value)
  return currentMember?.role === 'admin'
})

const hasProgenitor = computed(() => lineage.value?.progenitor_person_id !== null)

const sortedGenerations = computed(() => {
  if (!lineage.value) return []
  return [...lineage.value.generations].sort((a, b) => a.index - b.index)
})

const progenitorPickerOpen = ref(false)
const selectedProgenitorId = ref('')
const submitting = ref(false)
const clearConfirmOpen = ref(false)
const changeConfirmOpen = ref(false)

const generationNamesEditorOpen = ref(false)
const editingGenerationNames = ref<string[]>([])
const editingGenerationNameAlign = ref<GenerationNameAlign>('A')
const generationNamesSubmitting = ref(false)
const generationNamesError = ref<string | null>(null)

const availablePersons = computed(() => {
  return persons.value.filter((p) => p.id !== lineage.value?.progenitor_person_id)
})

async function loadLineage() {
  if (!currentFamilyId.value) {
    loadError.value = '未选择家族'
    return
  }

  if (!usingGraphApi.value) {
    loadError.value = '世系视图需要连接真实 API（当前为 mock 模式）'
    return
  }

  loading.value = true
  loadError.value = null

  try {
    lineage.value = await fetchLineage({
      familyId: currentFamilyId.value,
    })
  } catch (e) {
    if (e instanceof LineageApiError) {
      loadError.value = e.message
    } else {
      loadError.value = '加载世系视图失败'
    }
    lineage.value = null
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  if (!currentFamilyId.value || !usingGraphApi.value) return

  try {
    const result = await listMembers(currentFamilyId.value)
    members.value = result.members
  } catch (e) {
    if (e instanceof MemberApiError) {
      console.warn('Failed to load members:', e.message)
    }
  }
}

async function loadPersons() {
  if (!currentFamilyId.value || !usingGraphApi.value) return

  try {
    const result = await apiListPersons(currentFamilyId.value)
    persons.value = result.persons
  } catch (e) {
    console.warn('Failed to load persons:', e)
  }
}

function openProgenitorPicker() {
  selectedProgenitorId.value = ''
  actionError.value = null
  progenitorPickerOpen.value = true
}

function closeProgenitorPicker() {
  progenitorPickerOpen.value = false
  selectedProgenitorId.value = ''
}

function openChangeConfirm() {
  actionError.value = null
  changeConfirmOpen.value = true
}

function closeChangeConfirm() {
  changeConfirmOpen.value = false
  selectedProgenitorId.value = ''
}

function openClearConfirm() {
  actionError.value = null
  clearConfirmOpen.value = true
}

function closeClearConfirm() {
  clearConfirmOpen.value = false
}

async function handleSetProgenitor() {
  if (!currentFamilyId.value || !selectedProgenitorId.value) return

  submitting.value = true
  actionError.value = null

  try {
    await setProgenitor({
      familyId: currentFamilyId.value,
      personId: selectedProgenitorId.value,
    })
    closeProgenitorPicker()
    closeChangeConfirm()
    await loadLineage()
  } catch (e) {
    if (e instanceof LineageApiError) {
      actionError.value = e.message
    } else {
      actionError.value = '设置始迁祖失败'
    }
  } finally {
    submitting.value = false
  }
}

async function handleClearProgenitor() {
  if (!currentFamilyId.value) return

  submitting.value = true
  actionError.value = null

  try {
    await setProgenitor({
      familyId: currentFamilyId.value,
      personId: null,
    })
    closeClearConfirm()
    await loadLineage()
  } catch (e) {
    if (e instanceof LineageApiError) {
      actionError.value = e.message
    } else {
      actionError.value = '清除始迁祖失败'
    }
  } finally {
    submitting.value = false
  }
}

async function handlePersonClick(personId: string) {
  await store.selectPersonWithFocus(personId)
}

function formatGenerationName(name: string | null | undefined): { text: string; type: 'normal' | 'empty-slot' | 'none' } {
  if (name === null || name === undefined) {
    return { text: '—', type: 'none' }
  }
  if (name === '') {
    return { text: '空档', type: 'empty-slot' }
  }
  return { text: name, type: 'normal' }
}

function getAlignLabel(align: GenerationNameAlign): string {
  if (align === 'A') {
    return 'A（第2世起）'
  }
  return 'B（第1世起）'
}

async function openGenerationNamesEditor() {
  generationNamesError.value = null
  generationNamesSubmitting.value = false

  try {
    const config = await fetchGenerationNames({ familyId: currentFamilyId.value! })
    editingGenerationNames.value = config.generation_names ? [...config.generation_names] : []
    editingGenerationNameAlign.value = config.generation_name_align || 'A'
  } catch (e) {
    editingGenerationNames.value = []
    editingGenerationNameAlign.value = lineage.value?.generation_name_align || 'A'
  }

  generationNamesEditorOpen.value = true
}

function closeGenerationNamesEditor() {
  generationNamesEditorOpen.value = false
  editingGenerationNames.value = []
  generationNamesError.value = null
}

function addGenerationNameEntry() {
  editingGenerationNames.value.push('')
}

function removeGenerationNameEntry(index: number) {
  editingGenerationNames.value.splice(index, 1)
}

function moveGenerationNameUp(index: number) {
  if (index <= 0) return
  const temp = editingGenerationNames.value[index]
  editingGenerationNames.value[index] = editingGenerationNames.value[index - 1]
  editingGenerationNames.value[index - 1] = temp
}

function moveGenerationNameDown(index: number) {
  if (index >= editingGenerationNames.value.length - 1) return
  const temp = editingGenerationNames.value[index]
  editingGenerationNames.value[index] = editingGenerationNames.value[index + 1]
  editingGenerationNames.value[index + 1] = temp
}

async function handleSaveGenerationNames() {
  if (!currentFamilyId.value) return

  const validationError = validateGenerationNames(editingGenerationNames.value)
  if (validationError) {
    generationNamesError.value = validationError
    return
  }

  generationNamesSubmitting.value = true
  generationNamesError.value = null

  try {
    await updateGenerationNames({
      familyId: currentFamilyId.value,
      generationNames: editingGenerationNames.value,
      generationNameAlign: editingGenerationNameAlign.value,
    })
    closeGenerationNamesEditor()
    await loadLineage()
  } catch (e) {
    if (e instanceof GenerationNamesApiError) {
      generationNamesError.value = e.message
    } else {
      generationNamesError.value = '保存字辈设置失败'
    }
  } finally {
    generationNamesSubmitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadLineage(), loadMembers(), loadPersons()])
})

watch(currentFamilyId, async () => {
  await Promise.all([loadLineage(), loadMembers(), loadPersons()])
})

defineExpose({ reload: loadLineage })
</script>

<template>
  <div class="lineage-view">
    <!-- Loading state -->
    <div v-if="loading" class="state-loading">
      <p class="loading-text">加载世系视图…</p>
    </div>

    <!-- Load error state -->
    <div v-else-if="loadError" class="state-error" role="alert">
      <p class="error-text">{{ loadError }}</p>
      <button
        v-if="usingGraphApi"
        type="button"
        class="btn-retry"
        @click="loadLineage"
      >
        重试
      </button>
    </div>

    <!-- Empty state: no progenitor -->
    <div v-else-if="!hasProgenitor" class="state-empty">
      <div class="empty-content">
        <h3 class="empty-title">尚未指定始迁祖</h3>
        <p class="empty-desc">
          世系按「始迁祖」往下展开，显示各世代直系后裔。
        </p>
        <p class="empty-hint">
          始迁祖为第1世，其生物学子女为第2世，以此类推。
        </p>
        <template v-if="isAdmin">
          <button
            type="button"
            class="btn-cta"
            @click="openProgenitorPicker"
          >
            指定始迁祖
          </button>
        </template>
        <template v-else>
          <p class="admin-notice">请联系家族管理员指定始迁祖</p>
        </template>
      </div>
    </div>

    <!-- Lineage layers -->
    <div v-else class="lineage-content">
      <!-- Admin actions bar -->
      <div v-if="isAdmin" class="admin-bar">
        <span class="admin-bar-label">管理员操作：</span>
        <button
          type="button"
          class="btn-admin"
          @click="openProgenitorPicker"
        >
          更换始迁祖
        </button>
        <button
          type="button"
          class="btn-admin btn-admin-danger"
          @click="openClearConfirm"
        >
          清除始迁祖
        </button>
        <span class="admin-bar-sep">|</span>
        <button
          type="button"
          class="btn-admin"
          @click="openGenerationNamesEditor"
        >
          编辑字辈
        </button>
      </div>

      <div class="lineage-layers">
        <div
          v-for="gen in sortedGenerations"
          :key="gen.index"
          class="generation-layer"
          :class="{ 'is-progenitor': gen.index === 1 }"
        >
          <div class="layer-header">
            <h3 class="layer-label">{{ getLineageLabel(gen.index) }}</h3>
            <span
              v-if="formatGenerationName(gen.generation_name).type === 'normal'"
              class="gen-name-badge gen-name-normal"
              :title="'期望辈字：' + gen.generation_name"
            >
              {{ formatGenerationName(gen.generation_name).text }}
            </span>
            <span
              v-else-if="formatGenerationName(gen.generation_name).type === 'empty-slot'"
              class="gen-name-badge gen-name-empty-slot"
              title="此世空档（无辈字字符）"
            >
              {{ formatGenerationName(gen.generation_name).text }}
            </span>
            <span
              v-else
              class="gen-name-badge gen-name-none"
              title="无辈字"
            >
              {{ formatGenerationName(gen.generation_name).text }}
            </span>
          </div>
          <ul class="persons-list">
            <li
              v-for="person in gen.persons"
              :key="person.id"
              class="person-item"
              :class="{ 'has-conflict': person.conflict === true }"
            >
              <button
                type="button"
                class="person-button"
                :title="person.display_name + (person.conflict ? '（世次冲突）' : '')"
                @click="handlePersonClick(person.id)"
              >
                <span class="person-name">{{ person.display_name }}</span>
                <span
                  v-if="person.conflict"
                  class="conflict-badge"
                  aria-label="世次冲突"
                >!</span>
              </button>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <!-- Progenitor picker modal -->
    <div v-if="progenitorPickerOpen" class="modal-overlay" @click.self="closeProgenitorPicker">
      <div class="modal-box">
        <h2 class="modal-title">{{ hasProgenitor ? '更换始迁祖' : '指定始迁祖' }}</h2>
        <p class="modal-desc">
          选择一位家族成员作为始迁祖（第1世），世系将从此人向下展开。
        </p>
        <!-- Action error in modal -->
        <div v-if="actionError && !changeConfirmOpen" class="modal-error" role="alert">
          {{ actionError }}
        </div>
        <div class="form-row">
          <label class="form-label">选择成员</label>
          <select
            v-model="selectedProgenitorId"
            class="form-select"
            :disabled="submitting"
          >
            <option value="">请选择...</option>
            <option
              v-for="p in availablePersons"
              :key="p.id"
              :value="p.id"
            >
              {{ p.last_name }}{{ p.first_name }}
            </option>
          </select>
        </div>
        <div class="form-actions">
          <button
            v-if="!hasProgenitor"
            type="button"
            class="btn-submit"
            :disabled="submitting || !selectedProgenitorId"
            @click="handleSetProgenitor"
          >
            {{ submitting ? '设置中…' : '确认' }}
          </button>
          <button
            v-else
            type="button"
            class="btn-submit"
            :disabled="submitting || !selectedProgenitorId"
            @click="openChangeConfirm"
          >
            下一步
          </button>
          <button
            type="button"
            class="btn-cancel"
            :disabled="submitting"
            @click="closeProgenitorPicker"
          >
            取消
          </button>
        </div>
      </div>
    </div>

    <!-- Change progenitor confirmation modal -->
    <div v-if="changeConfirmOpen" class="modal-overlay" @click.self="closeChangeConfirm">
      <div class="modal-box">
        <h2 class="modal-title">确认更换始迁祖</h2>
        <p class="modal-desc modal-warn">
          更换始迁祖后，世系视图将重新计算，原有的世次将改变。
        </p>
        <!-- Action error in confirm modal -->
        <div v-if="actionError" class="modal-error" role="alert">
          {{ actionError }}
        </div>
        <div class="form-actions">
          <button
            type="button"
            class="btn-submit btn-danger"
            :disabled="submitting"
            @click="handleSetProgenitor"
          >
            {{ submitting ? '更换中…' : '确认更换' }}
          </button>
          <button
            type="button"
            class="btn-cancel"
            :disabled="submitting"
            @click="closeChangeConfirm"
          >
            取消
          </button>
        </div>
      </div>
    </div>

    <!-- Clear progenitor confirmation modal -->
    <div v-if="clearConfirmOpen" class="modal-overlay" @click.self="closeClearConfirm">
      <div class="modal-box">
        <h2 class="modal-title">确认清除始迁祖</h2>
        <p class="modal-desc modal-warn">
          清除始迁祖后，世系视图将显示空状态，需要重新指定始迁祖。
        </p>
        <!-- Action error in confirm modal -->
        <div v-if="actionError" class="modal-error" role="alert">
          {{ actionError }}
        </div>
        <div class="form-actions">
          <button
            type="button"
            class="btn-submit btn-danger"
            :disabled="submitting"
            @click="handleClearProgenitor"
          >
            {{ submitting ? '清除中…' : '确认清除' }}
          </button>
          <button
            type="button"
            class="btn-cancel"
            :disabled="submitting"
            @click="closeClearConfirm"
          >
            取消
          </button>
        </div>
      </div>
    </div>

    <!-- Generation names editor modal -->
    <div v-if="generationNamesEditorOpen" class="modal-overlay" @click.self="closeGenerationNamesEditor">
      <div class="modal-box modal-box-wide">
        <h2 class="modal-title">编辑字辈</h2>
        <p class="modal-desc">
          设置家族字辈（辈字诗），用于标识各世代期望的命名字符。留空表示该世空档。
        </p>

        <!-- Align selection -->
        <div class="form-row">
          <label class="form-label">对齐方式</label>
          <div class="align-options">
            <label class="align-option">
              <input
                type="radio"
                v-model="editingGenerationNameAlign"
                value="A"
                :disabled="generationNamesSubmitting"
              />
              <span class="align-text">
                <strong>A</strong>：第1世无辈字，第2世起使用（默认）
              </span>
            </label>
            <label class="align-option">
              <input
                type="radio"
                v-model="editingGenerationNameAlign"
                value="B"
                :disabled="generationNamesSubmitting"
              />
              <span class="align-text">
                <strong>B</strong>：第1世起使用辈字
              </span>
            </label>
          </div>
        </div>

        <!-- Generation names list -->
        <div class="form-row">
          <label class="form-label">字辈序列（最多200个，每个最多16字符）</label>
          <div class="gen-names-list">
            <div
              v-for="(name, index) in editingGenerationNames"
              :key="index"
              class="gen-name-row"
            >
              <span class="gen-name-index">{{ index + 1 }}</span>
              <input
                type="text"
                v-model="editingGenerationNames[index]"
                class="gen-name-input"
                :placeholder="'第' + (index + 1) + '个'"
                maxlength="16"
                :disabled="generationNamesSubmitting"
              />
              <button
                type="button"
                class="gen-name-btn"
                title="上移"
                :disabled="generationNamesSubmitting || index === 0"
                @click="moveGenerationNameUp(index)"
              >
                ↑
              </button>
              <button
                type="button"
                class="gen-name-btn"
                title="下移"
                :disabled="generationNamesSubmitting || index === editingGenerationNames.length - 1"
                @click="moveGenerationNameDown(index)"
              >
                ↓
              </button>
              <button
                type="button"
                class="gen-name-btn gen-name-btn-danger"
                title="删除"
                :disabled="generationNamesSubmitting"
                @click="removeGenerationNameEntry(index)"
              >
                ×
              </button>
            </div>
            <div v-if="editingGenerationNames.length === 0" class="gen-names-empty">
              尚未添加字辈
            </div>
          </div>
          <button
            type="button"
            class="btn-add-entry"
            :disabled="generationNamesSubmitting || editingGenerationNames.length >= 200"
            @click="addGenerationNameEntry"
          >
            + 添加字辈
          </button>
        </div>

        <!-- Error display -->
        <div v-if="generationNamesError" class="modal-error" role="alert">
          {{ generationNamesError }}
        </div>

        <!-- Actions -->
        <div class="form-actions">
          <button
            type="button"
            class="btn-submit"
            :disabled="generationNamesSubmitting"
            @click="handleSaveGenerationNames"
          >
            {{ generationNamesSubmitting ? '保存中…' : '保存' }}
          </button>
          <button
            type="button"
            class="btn-cancel"
            :disabled="generationNamesSubmitting"
            @click="closeGenerationNamesEditor"
          >
            取消
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lineage-view {
  height: 100%;
  overflow: auto;
  padding: 20px;
  font-family: var(--font-cn, sans-serif);
  color: var(--color-ink, #1f1f1f);
  background: var(--color-paper, #faf9f7);
}

/* Loading state */
.state-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.loading-text {
  font-size: 16px;
  color: #666;
}

/* Error state */
.state-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 16px;
}
.error-text {
  font-size: 15px;
  color: #c53030;
  text-align: center;
  margin: 0;
}
.btn-retry {
  padding: 8px 20px;
  border: 1px solid var(--color-accent, #2f5d50);
  border-radius: 6px;
  background: transparent;
  color: var(--color-accent, #2f5d50);
  font-size: 14px;
  cursor: pointer;
}
.btn-retry:hover {
  background: rgba(47, 93, 80, 0.08);
}

/* Empty state */
.state-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.empty-content {
  text-align: center;
  max-width: 400px;
}
.empty-title {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 600;
  color: #333;
}
.empty-desc {
  margin: 0 0 12px;
  font-size: 15px;
  color: #666;
  line-height: 1.6;
}
.empty-hint {
  margin: 0 0 24px;
  font-size: 14px;
  color: #888;
}
.btn-cta {
  padding: 12px 28px;
  border: none;
  border-radius: 8px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-cta:hover {
  opacity: 0.9;
}
.admin-notice {
  margin: 0;
  font-size: 14px;
  color: #888;
  font-style: italic;
}

/* Lineage content */
.lineage-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Admin bar */
.admin-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #f5f4f3;
  border-radius: 8px;
}
.admin-bar-label {
  font-size: 14px;
  color: #666;
}
.btn-admin {
  padding: 6px 14px;
  border: 1px solid var(--color-accent, #2f5d50);
  border-radius: 6px;
  background: #fff;
  color: var(--color-accent, #2f5d50);
  font-size: 13px;
  cursor: pointer;
}
.btn-admin:hover {
  background: rgba(47, 93, 80, 0.08);
}
.btn-admin-danger {
  border-color: #c53030;
  color: #c53030;
}
.btn-admin-danger:hover {
  background: #fef0f0;
}
.admin-bar-sep {
  color: #ccc;
  margin: 0 4px;
}

/* Lineage layers */
.lineage-layers {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.generation-layer {
  padding: 16px;
  background: var(--color-card, #fff);
  border-radius: 10px;
  border: 1px solid var(--color-border, #d8d4cc);
}
.generation-layer.is-progenitor {
  border-color: var(--color-accent, #2f5d50);
  border-width: 2px;
  background: #f8fbfa;
}

.layer-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.layer-label {
  margin: 0;
  font-size: var(--type-detail-section, 16px);
  font-weight: 600;
  color: var(--color-ink, #1f1f1f);
}
.generation-layer.is-progenitor .layer-label {
  color: var(--color-accent, #2f5d50);
}

.gen-name-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
}
.gen-name-normal {
  background: #e8f5e9;
  color: #2e7d32;
  border: 1px solid #a5d6a7;
}
.gen-name-empty-slot {
  background: #fff8e1;
  color: #f9a825;
  border: 1px solid #ffe082;
  font-size: 12px;
}
.gen-name-none {
  background: #f5f5f5;
  color: #9e9e9e;
  border: 1px solid #e0e0e0;
}

.persons-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.person-item {
  position: relative;
}

.person-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  background: #faf9f7;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s, border-color 0.15s;
}
.person-button:hover {
  background: #f0eeeb;
  border-color: var(--color-accent, #2f5d50);
}
.person-item.has-conflict .person-button {
  border-color: #e6a23c;
}

.person-name {
  font-size: var(--type-node-name, 18px);
  font-weight: 500;
  line-height: 1.3;
}

.conflict-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff4e5;
  color: #e6a23c;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

/* Modal overlay */
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
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
}
.modal-desc {
  margin: 0 0 20px;
  font-size: 14px;
  color: #555;
  line-height: 1.6;
}
.modal-desc.modal-warn {
  padding: 12px;
  background: #fff4e5;
  border-radius: 6px;
  color: #8a6d3b;
}
.modal-error {
  margin: 0 0 16px;
  padding: 10px 12px;
  background: #fef0f0;
  border: 1px solid #f5c6cb;
  border-radius: 6px;
  color: #c53030;
  font-size: 14px;
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
.btn-submit.btn-danger {
  background: #c53030;
}
.btn-submit.btn-danger:hover:not(:disabled) {
  background: #9b2c2c;
}
.btn-cancel {
  padding: 10px 16px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: #fff;
  font-size: 15px;
  cursor: pointer;
}
.btn-cancel:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Generation names editor modal */
.modal-box-wide {
  width: min(500px, 90%);
  max-height: 80vh;
  overflow-y: auto;
}

.align-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.align-option {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
}
.align-option input {
  margin-top: 3px;
}
.align-text {
  font-size: 14px;
  color: #555;
  line-height: 1.4;
}
.align-text strong {
  color: #333;
}

.gen-names-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  max-height: 300px;
  overflow-y: auto;
  padding: 8px;
  background: #faf9f7;
  border-radius: 6px;
  border: 1px solid var(--color-border, #d8d4cc);
}
.gen-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.gen-name-index {
  width: 28px;
  font-size: 13px;
  color: #888;
  text-align: right;
  flex-shrink: 0;
}
.gen-name-input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
}
.gen-name-input:focus {
  outline: none;
  border-color: var(--color-accent, #2f5d50);
}
.gen-name-input:disabled {
  background: #f0eeeb;
  cursor: not-allowed;
}
.gen-name-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  color: #666;
  flex-shrink: 0;
}
.gen-name-btn:hover:not(:disabled) {
  background: #f5f4f3;
  border-color: var(--color-accent, #2f5d50);
  color: var(--color-accent, #2f5d50);
}
.gen-name-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.gen-name-btn-danger:hover:not(:disabled) {
  background: #fef0f0;
  border-color: #c53030;
  color: #c53030;
}
.gen-names-empty {
  text-align: center;
  color: #999;
  font-size: 14px;
  padding: 16px;
}

.btn-add-entry {
  padding: 8px 16px;
  border: 1px dashed var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: transparent;
  color: var(--color-accent, #2f5d50);
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.btn-add-entry:hover:not(:disabled) {
  background: rgba(47, 93, 80, 0.05);
  border-color: var(--color-accent, #2f5d50);
}
.btn-add-entry:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
