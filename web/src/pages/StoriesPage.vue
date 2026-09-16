<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { isUsingGraphApi } from '../features/tree/api/graphClient'
import { getCurrentUserId } from '../features/tree/api/auth'
import {
  listStories,
  createStory,
  updateStory,
  deleteStory,
  formatNarrativeTime,
  StoryApiError,
  STORY_BODY_MAX_LENGTH,
} from '../features/tree/api/storyClient'
import {
  listMembers,
  type FamilyMember,
  MemberApiError,
} from '../features/tree/api/memberClient'
import {
  listPersons,
  type PersonResponse,
} from '../features/tree/api/personClient'
import type { StoryResponse } from '../features/tree/api/types'
import StoryCommentsPanel from '../features/tree/components/StoryCommentsPanel.vue'

type ViewMode = 'list' | 'detail' | 'create' | 'edit'

const router = useRouter()
const route = useRoute()

const stories = ref<StoryResponse[]>([])
const selectedStory = ref<StoryResponse | null>(null)
const persons = ref<PersonResponse[]>([])
const members = ref<FamilyMember[]>([])

const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const successMessage = ref('')
const submitting = ref(false)

const viewMode = ref<ViewMode>('list')

const formTitle = ref('')
const formBody = ref('')
const formNarrativeTime = ref('')
const formPersonIds = ref<string[]>([])
const formVersion = ref(0)

const deleteConfirmOpen = ref(false)
const storyToDelete = ref<StoryResponse | null>(null)

const usingGraphApi = computed(() => isUsingGraphApi())
const familyId = computed(() => route.query.familyId as string | undefined)
const currentUserId = computed(() => getCurrentUserId() ?? '')

const isAdminOrEditor = computed(() => {
  if (!currentUserId.value) return false
  const currentMember = members.value.find((m) => m.user_id === currentUserId.value)
  return currentMember?.role === 'admin' || currentMember?.role === 'editor'
})

const isAdmin = computed(() => {
  if (!currentUserId.value) return false
  const currentMember = members.value.find((m) => m.user_id === currentUserId.value)
  return currentMember?.role === 'admin'
})

const canSubmit = computed(() => {
  const body = formBody.value.trim()
  if (!body || submitting.value) return false
  if (body.length > STORY_BODY_MAX_LENGTH) return false
  return true
})

const bodyCharCount = computed(() => formBody.value.length)
const isBodyOverLimit = computed(() => formBody.value.length > STORY_BODY_MAX_LENGTH)

function getPersonName(personId: string): string {
  const person = persons.value.find((p) => p.id === personId)
  if (person) {
    return `${person.last_name}${person.first_name}`
  }
  return personId.slice(0, 8) + '...'
}

function getLinkedPersonsLabel(story: StoryResponse): string {
  if (story.person_ids.length === 0) {
    return '家族级'
  }
  if (story.person_ids.length === 1) {
    return getPersonName(story.person_ids[0])
  }
  return `${story.person_ids.length} 人相关`
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

async function consumePreSelectPerson() {
  const preSelectPerson = route.query.preSelectPerson as string | undefined
  if (!preSelectPerson || !familyId.value) return

  router.replace({
    query: {
      ...route.query,
      preSelectPerson: undefined,
    },
  })

  if (!isAdminOrEditor.value) return

  const personExistsInFamily = persons.value.some((p) => p.id === preSelectPerson)
  if (personExistsInFamily) {
    openCreateForm(preSelectPerson)
  }
}

onMounted(async () => {
  if (familyId.value && usingGraphApi.value) {
    await Promise.all([loadStories(), loadMembers(), loadPersons()])
    await consumePreSelectPerson()
  }
})

watch(
  () => route.query.familyId,
  async (newFamilyId, oldFamilyId) => {
    if (newFamilyId !== oldFamilyId && newFamilyId && usingGraphApi.value) {
      viewMode.value = 'list'
      await Promise.all([loadStories(), loadMembers(), loadPersons()])
    }
  },
)

watch(
  () => route.query.preSelectPerson,
  async (newPreSelect) => {
    if (newPreSelect && familyId.value && usingGraphApi.value && members.value.length > 0) {
      await consumePreSelectPerson()
    }
  },
)

async function loadStories() {
  if (!familyId.value) return

  loading.value = true
  loadError.value = ''
  stories.value = []
  selectedStory.value = null

  try {
    const result = await listStories({ familyId: familyId.value })
    stories.value = result.stories
  } catch (err) {
    stories.value = []
    if (err instanceof StoryApiError) {
      loadError.value = err.message
    } else {
      loadError.value = '加载故事列表失败'
    }
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  if (!familyId.value || !usingGraphApi.value) return

  try {
    const result = await listMembers(familyId.value)
    members.value = result.members
  } catch (e) {
    if (e instanceof MemberApiError) {
      console.warn('Failed to load members:', e.message)
    }
  }
}

async function loadPersons() {
  if (!familyId.value || !usingGraphApi.value) return

  try {
    const result = await listPersons(familyId.value)
    persons.value = result.persons
  } catch (e) {
    console.warn('Failed to load persons:', e)
  }
}

function openDetail(story: StoryResponse) {
  selectedStory.value = story
  viewMode.value = 'detail'
  actionError.value = ''
}

function openCreateForm(preSelectPersonId?: string) {
  formTitle.value = ''
  formBody.value = ''
  formNarrativeTime.value = ''
  formPersonIds.value = preSelectPersonId ? [preSelectPersonId] : []
  formVersion.value = 0
  viewMode.value = 'create'
  actionError.value = ''
}

function openEditForm(story: StoryResponse) {
  selectedStory.value = story
  formTitle.value = story.title || ''
  formBody.value = story.body
  formNarrativeTime.value = story.narrative_time || ''
  formPersonIds.value = [...story.person_ids]
  formVersion.value = story.version
  viewMode.value = 'edit'
  actionError.value = ''
}

function goBackToList() {
  viewMode.value = 'list'
  selectedStory.value = null
  actionError.value = ''
}

function goBackToDetail() {
  viewMode.value = 'detail'
  actionError.value = ''
}

async function handleCreate() {
  if (!canSubmit.value || !familyId.value) return

  submitting.value = true
  actionError.value = ''
  successMessage.value = ''

  try {
    const newStory = await createStory(familyId.value, {
      title: formTitle.value.trim() || null,
      body: formBody.value.trim(),
      narrative_time: formNarrativeTime.value.trim() || null,
      person_ids: formPersonIds.value,
    })
    stories.value = [newStory, ...stories.value]
    selectedStory.value = newStory
    viewMode.value = 'detail'
    successMessage.value = '故事创建成功'
    setTimeout(() => { successMessage.value = '' }, 3000)
  } catch (err) {
    if (err instanceof StoryApiError) {
      actionError.value = err.message
    } else {
      actionError.value = '创建故事失败'
    }
  } finally {
    submitting.value = false
  }
}

async function handleUpdate() {
  if (!canSubmit.value || !familyId.value || !selectedStory.value) return

  submitting.value = true
  actionError.value = ''
  successMessage.value = ''

  try {
    const updatedStory = await updateStory(
      familyId.value,
      selectedStory.value.id,
      {
        title: formTitle.value.trim() || null,
        body: formBody.value.trim(),
        narrative_time: formNarrativeTime.value.trim() || null,
        person_ids: formPersonIds.value,
        version: formVersion.value,
      },
    )
    stories.value = stories.value.map((s) =>
      s.id === updatedStory.id ? updatedStory : s,
    )
    selectedStory.value = updatedStory
    viewMode.value = 'detail'
    successMessage.value = '故事更新成功'
    setTimeout(() => { successMessage.value = '' }, 3000)
  } catch (err) {
    if (err instanceof StoryApiError) {
      if (err.code === 'VERSION_CONFLICT' && err.conflictStory) {
        formTitle.value = err.conflictStory.title || ''
        formBody.value = err.conflictStory.body
        formNarrativeTime.value = err.conflictStory.narrative_time || ''
        formPersonIds.value = [...err.conflictStory.person_ids]
        formVersion.value = err.conflictStory.version
        selectedStory.value = err.conflictStory
        stories.value = stories.value.map((s) =>
          s.id === err.conflictStory!.id ? err.conflictStory! : s,
        )
      }
      actionError.value = err.message
    } else {
      actionError.value = '更新故事失败'
    }
  } finally {
    submitting.value = false
  }
}

function openDeleteConfirm(story: StoryResponse) {
  storyToDelete.value = story
  deleteConfirmOpen.value = true
  actionError.value = ''
}

function closeDeleteConfirm() {
  deleteConfirmOpen.value = false
  storyToDelete.value = null
}

async function handleDelete() {
  if (!familyId.value || !storyToDelete.value) return

  const deletingStoryId = storyToDelete.value.id
  const deletingStoryVersion = storyToDelete.value.version
  const wasSelectedStory = selectedStory.value?.id === deletingStoryId

  submitting.value = true
  actionError.value = ''
  successMessage.value = ''

  try {
    await deleteStory(familyId.value, deletingStoryId, deletingStoryVersion)
    stories.value = stories.value.filter((s) => s.id !== deletingStoryId)
    if (wasSelectedStory) {
      selectedStory.value = null
      viewMode.value = 'list'
    }
    closeDeleteConfirm()
    successMessage.value = '故事已删除'
    setTimeout(() => { successMessage.value = '' }, 3000)
  } catch (err) {
    if (err instanceof StoryApiError) {
      if (err.code === 'VERSION_CONFLICT' && err.conflictStory) {
        stories.value = stories.value.map((s) =>
          s.id === err.conflictStory!.id ? err.conflictStory! : s,
        )
        if (selectedStory.value?.id === err.conflictStory.id) {
          selectedStory.value = err.conflictStory
        }
        storyToDelete.value = err.conflictStory
      }
      actionError.value = err.message
    } else {
      actionError.value = '删除故事失败'
    }
  } finally {
    submitting.value = false
  }
}

function togglePersonId(personId: string) {
  const idx = formPersonIds.value.indexOf(personId)
  if (idx >= 0) {
    formPersonIds.value.splice(idx, 1)
  } else {
    formPersonIds.value.push(personId)
  }
}

function goToTree() {
  if (familyId.value) {
    router.push({ name: 'tree', query: { familyId: familyId.value } })
  } else {
    router.push({ name: 'tree' })
  }
}

function handleCommentError(message: string) {
  actionError.value = message
}
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div>
        <h1>家族故事</h1>
        <p class="sub">
          <template v-if="usingGraphApi">
            已接真 API ·
            <code>GET/POST /api/v1/families/{'{familyId}'}/stories</code>
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
      </div>
    </header>

    <main class="content">
      <div v-if="loadError && viewMode === 'list'" class="toast toast-error" role="alert">
        {{ loadError }}
      </div>
      <div v-if="actionError" class="toast toast-error" role="alert">
        {{ actionError }}
      </div>
      <div v-if="successMessage" class="toast toast-success" role="status">
        {{ successMessage }}
      </div>

      <div v-if="!familyId" class="warning-card">
        <p class="warning-title">未指定家族</p>
        <p class="warning-desc">请先从家族树中选择一个家族</p>
        <div class="warning-actions">
          <button type="button" class="btn-primary" @click="goToTree">
            前往家族树
          </button>
        </div>
      </div>

      <template v-else>
        <div class="info-bar">
          <span class="info-label">当前家族 ID：</span>
          <code class="info-value">{{ familyId }}</code>
        </div>

        <!-- List View -->
        <div v-if="viewMode === 'list'" class="stories-card">
          <div class="card-header">
            <h2 class="card-title">故事列表</h2>
            <button
              v-if="isAdminOrEditor && usingGraphApi"
              type="button"
              class="btn-primary-sm"
              @click="openCreateForm()"
            >
              新建故事
            </button>
          </div>

          <div v-if="!usingGraphApi" class="warning-banner">
            需要连接真实 API。请设置环境变量 VITE_USE_GRAPH_API=true 或 VITE_GRAPH_API_BASE。
          </div>

          <div v-else-if="loading" class="loading-text">加载中…</div>

          <div v-else-if="stories.length === 0" class="empty-text">
            暂无故事
          </div>

          <ul v-else class="story-list">
            <li
              v-for="story in stories"
              :key="story.id"
              class="story-item"
              @click="openDetail(story)"
            >
              <div class="story-main">
                <h3 class="story-title">{{ story.title || '无标题' }}</h3>
                <p class="story-meta">
                  <span class="meta-item">{{ getLinkedPersonsLabel(story) }}</span>
                  <span v-if="story.narrative_time" class="meta-item">
                    {{ formatNarrativeTime(story.narrative_time) }}
                  </span>
                </p>
              </div>
              <div class="story-side">
                <span class="story-date">{{ formatDate(story.updated_at) }}</span>
              </div>
            </li>
          </ul>
        </div>

        <!-- Detail View -->
        <div v-else-if="viewMode === 'detail' && selectedStory" class="detail-card">
          <div class="card-header">
            <button type="button" class="btn-back" @click="goBackToList">
              ← 返回列表
            </button>
            <div v-if="isAdminOrEditor" class="header-actions">
              <button type="button" class="btn-edit" @click="openEditForm(selectedStory)">
                编辑
              </button>
              <button type="button" class="btn-delete" @click="openDeleteConfirm(selectedStory)">
                删除
              </button>
            </div>
          </div>

          <h2 class="detail-title">{{ selectedStory.title || '无标题' }}</h2>

          <div class="detail-meta">
            <div class="meta-row">
              <span class="meta-label">关联对象：</span>
              <span v-if="selectedStory.person_ids.length === 0" class="meta-value">家族级故事</span>
              <span v-else class="meta-value persons-list">
                <span
                  v-for="pid in selectedStory.person_ids"
                  :key="pid"
                  class="person-tag"
                >
                  {{ getPersonName(pid) }}
                </span>
              </span>
            </div>
            <div v-if="selectedStory.narrative_time" class="meta-row">
              <span class="meta-label">叙事时间：</span>
              <span class="meta-value">{{ formatNarrativeTime(selectedStory.narrative_time) }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">更新时间：</span>
              <span class="meta-value">{{ formatDate(selectedStory.updated_at) }}</span>
            </div>
            <div class="meta-row meta-minor">
              <span class="meta-label">版本：</span>
              <span class="meta-value">{{ selectedStory.version }}</span>
            </div>
          </div>

          <div class="detail-body">
            <p class="body-text">{{ selectedStory.body }}</p>
          </div>

          <StoryCommentsPanel
            v-if="familyId && selectedStory"
            :family-id="familyId"
            :story-id="selectedStory.id"
            :current-user-id="currentUserId"
            :members="members"
            :is-admin="isAdmin"
            :can-write="isAdminOrEditor"
            @error="handleCommentError"
          />
        </div>

        <!-- Create/Edit Form -->
        <div v-else-if="viewMode === 'create' || viewMode === 'edit'" class="form-card">
          <div class="card-header">
            <button
              type="button"
              class="btn-back"
              @click="viewMode === 'edit' && selectedStory ? goBackToDetail() : goBackToList()"
            >
              ← {{ viewMode === 'edit' ? '返回详情' : '返回列表' }}
            </button>
          </div>

          <h2 class="card-title">{{ viewMode === 'create' ? '新建故事' : '编辑故事' }}</h2>

          <form @submit.prevent="viewMode === 'create' ? handleCreate() : handleUpdate()">
            <div class="form-row">
              <label class="form-label" for="title">标题（可选）</label>
              <input
                id="title"
                v-model="formTitle"
                type="text"
                class="form-input"
                placeholder="为故事起个标题"
                :disabled="submitting"
              />
            </div>

            <div class="form-row">
              <label class="form-label" for="body">内容</label>
              <textarea
                id="body"
                v-model="formBody"
                class="form-textarea"
                placeholder="写下家族故事..."
                rows="10"
                :disabled="submitting"
              ></textarea>
              <p class="form-hint" :class="{ 'hint-error': isBodyOverLimit }">
                {{ bodyCharCount }} / {{ STORY_BODY_MAX_LENGTH }} 字
              </p>
            </div>

            <div class="form-row">
              <label class="form-label" for="narrativeTime">叙事时间（可选）</label>
              <input
                id="narrativeTime"
                v-model="formNarrativeTime"
                type="date"
                class="form-input"
                :disabled="submitting"
              />
              <p class="form-hint">故事发生的大概时间</p>
            </div>

            <div class="form-row">
              <label class="form-label">关联成员（可选）</label>
              <p class="form-hint">不选则为家族级故事，选择后将与指定成员关联</p>
              <div class="person-picker">
                <label
                  v-for="person in persons"
                  :key="person.id"
                  class="person-option"
                  :class="{ selected: formPersonIds.includes(person.id) }"
                >
                  <input
                    type="checkbox"
                    :checked="formPersonIds.includes(person.id)"
                    :disabled="submitting"
                    @change="togglePersonId(person.id)"
                  />
                  <span class="person-name">{{ person.last_name }}{{ person.first_name }}</span>
                </label>
              </div>
            </div>

            <div class="form-actions">
              <button
                type="submit"
                class="btn-primary"
                :disabled="!canSubmit"
              >
                {{ submitting ? '保存中…' : (viewMode === 'create' ? '创建' : '保存') }}
              </button>
              <button
                type="button"
                class="btn-secondary"
                :disabled="submitting"
                @click="viewMode === 'edit' && selectedStory ? goBackToDetail() : goBackToList()"
              >
                取消
              </button>
            </div>
          </form>
        </div>
      </template>

      <!-- Delete Confirmation Modal -->
      <div v-if="deleteConfirmOpen" class="modal-overlay" @click.self="closeDeleteConfirm">
        <div class="modal-box">
          <h2 class="modal-title">确认删除</h2>
          <p class="modal-desc modal-warn">
            确定要删除故事「{{ storyToDelete?.title || '无标题' }}」吗？此操作无法撤销。
          </p>
          <div v-if="actionError" class="modal-error" role="alert">
            {{ actionError }}
          </div>
          <div class="form-actions">
            <button
              type="button"
              class="btn-danger"
              :disabled="submitting"
              @click="handleDelete"
            >
              {{ submitting ? '删除中…' : '确认删除' }}
            </button>
            <button
              type="button"
              class="btn-secondary"
              :disabled="submitting"
              @click="closeDeleteConfirm"
            >
              取消
            </button>
          </div>
        </div>
      </div>
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
  width: min(600px, 100%);
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

.stories-card,
.detail-card,
.form-card,
.warning-card {
  width: min(600px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.card-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.btn-back {
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: var(--color-accent, #2f5d50);
  font-size: 14px;
  cursor: pointer;
}

.btn-back:hover {
  text-decoration: underline;
}

.btn-edit,
.btn-delete {
  padding: 6px 12px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}

.btn-edit {
  color: var(--color-accent, #2f5d50);
  border-color: var(--color-accent, #2f5d50);
}

.btn-edit:hover {
  background: rgba(47, 93, 80, 0.08);
}

.btn-delete {
  color: #c53030;
  border-color: #c53030;
}

.btn-delete:hover {
  background: #fef0f0;
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

.story-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.story-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 8px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.story-item:last-child {
  margin-bottom: 0;
}

.story-item:hover {
  background: #faf9f7;
  border-color: var(--color-accent, #2f5d50);
}

.story-main {
  flex: 1;
  min-width: 0;
}

.story-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.story-meta {
  margin: 0;
  font-size: 13px;
  color: #888;
  display: flex;
  gap: 12px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
}

.story-side {
  flex-shrink: 0;
  margin-left: 16px;
}

.story-date {
  font-size: 12px;
  color: #999;
}

/* Detail View */
.detail-title {
  margin: 0 0 20px;
  font-size: 22px;
  font-weight: 600;
}

.detail-meta {
  padding: 16px;
  background: #faf9f7;
  border-radius: 8px;
  margin-bottom: 20px;
}

.meta-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 10px;
}

.meta-row:last-child {
  margin-bottom: 0;
}

.meta-row.meta-minor {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border, #d8d4cc);
  font-size: 12px;
  color: #888;
}

.meta-label {
  font-size: 14px;
  color: #666;
  min-width: 80px;
  flex-shrink: 0;
}

.meta-value {
  font-size: 14px;
  color: #333;
}

.meta-value.persons-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.person-tag {
  display: inline-block;
  padding: 2px 8px;
  background: #e8f0ed;
  border-radius: 4px;
  font-size: 13px;
  color: var(--color-accent, #2f5d50);
}

.detail-body {
  padding: 20px 0;
}

.body-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  color: #333;
}

/* Form */
.form-row {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: 15px;
  font-weight: 500;
  color: #333;
  margin-bottom: 6px;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  font-size: 15px;
  font-family: inherit;
  box-sizing: border-box;
  background: #fff;
}

.form-textarea {
  resize: vertical;
  min-height: 150px;
  line-height: 1.6;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--color-accent, #2f5d50);
}

.form-input:disabled,
.form-textarea:disabled {
  background: #f0eeeb;
  cursor: not-allowed;
}

.form-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #888;
}

.form-hint.hint-error {
  color: #c53030;
}

.person-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  max-height: 200px;
  overflow-y: auto;
  padding: 8px;
  background: #faf9f7;
  border-radius: 6px;
}

.person-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.person-option:hover {
  border-color: var(--color-accent, #2f5d50);
}

.person-option.selected {
  background: #e8f0ed;
  border-color: var(--color-accent, #2f5d50);
}

.person-option input[type='checkbox'] {
  display: none;
}

.person-option .person-name {
  font-size: 14px;
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}

.btn-primary,
.btn-danger {
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

.btn-danger {
  background: #c53030;
}

.btn-primary:hover:not(:disabled),
.btn-danger:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled,
.btn-danger:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.btn-primary-sm {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary-sm:hover {
  opacity: 0.9;
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

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toast {
  width: min(600px, 100%);
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

/* Modal */
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
</style>
