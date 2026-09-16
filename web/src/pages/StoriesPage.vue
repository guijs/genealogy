<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
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
import {
  fetchPersonRefCandidates,
  PersonRefApiError,
} from '../features/tree/api/personRefClient'
import type {
  StoryResponse,
  PersonRefRequest,
  PersonRefResponse,
  PersonRefCandidate,
} from '../features/tree/api/types'
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

const personRefCandidates = ref<PersonRefCandidate[]>([])
const formPersonRefs = ref<PersonRefRequest[]>([])
const formPersonRefsOriginal = ref<PersonRefRequest[]>([])
const showPersonRefDropdown = ref(false)
const personRefFilterText = ref('')
const personRefDropdownPosition = ref({ top: 0, left: 0 })
const formBodyTextareaRef = ref<HTMLTextAreaElement | null>(null)

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

const filteredPersonRefCandidates = computed(() => {
  const filter = personRefFilterText.value.toLowerCase()
  const selectedIds = new Set(formPersonRefs.value.map((r) => r.person_id))
  return personRefCandidates.value.filter((c) => {
    if (selectedIds.has(c.person_id)) return false
    return c.display_name.toLowerCase().includes(filter)
  })
})

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
    await Promise.all([loadStories(), loadMembers(), loadPersons(), loadPersonRefCandidates()])
    await consumePreSelectPerson()
  }
})

watch(
  () => route.query.familyId,
  async (newFamilyId, oldFamilyId) => {
    if (newFamilyId !== oldFamilyId && newFamilyId && usingGraphApi.value) {
      viewMode.value = 'list'
      await Promise.all([loadStories(), loadMembers(), loadPersons(), loadPersonRefCandidates()])
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

async function loadPersonRefCandidates() {
  if (!familyId.value || !usingGraphApi.value) return

  try {
    const result = await fetchPersonRefCandidates(familyId.value)
    personRefCandidates.value = result.candidates
  } catch (e) {
    if (e instanceof PersonRefApiError) {
      console.warn('Failed to load person ref candidates:', e.message)
    } else {
      console.warn('Failed to load person ref candidates:', e)
    }
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
  formPersonRefs.value = []
  formPersonRefsOriginal.value = []
  viewMode.value = 'create'
  actionError.value = ''
  closePersonRefDropdown()
}

function openEditForm(story: StoryResponse) {
  selectedStory.value = story
  formTitle.value = story.title || ''
  formBody.value = story.body
  formNarrativeTime.value = story.narrative_time || ''
  formPersonIds.value = [...story.person_ids]
  formVersion.value = story.version
  const activeRefs = story.person_refs
    ?.filter((r): r is PersonRefResponse & { person_id: string } => r.person_id !== null && r.clickable)
    .map((r) => ({
      person_id: r.person_id,
      display_name_snapshot: r.display_name_snapshot,
    })) ?? []
  formPersonRefs.value = [...activeRefs]
  formPersonRefsOriginal.value = [...activeRefs]
  viewMode.value = 'edit'
  actionError.value = ''
  closePersonRefDropdown()
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
    const createData: Parameters<typeof createStory>[1] = {
      title: formTitle.value.trim() || null,
      body: formBody.value.trim(),
      narrative_time: formNarrativeTime.value.trim() || null,
      person_ids: formPersonIds.value,
    }
    if (formPersonRefs.value.length > 0) {
      createData.person_refs = formPersonRefs.value
    }
    const newStory = await createStory(familyId.value, createData)
    stories.value = [newStory, ...stories.value]
    selectedStory.value = newStory
    viewMode.value = 'detail'
    successMessage.value = '故事创建成功'
    closePersonRefDropdown()
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
    const personRefsChanged = havePersonRefsChanged()
    const updateData: Parameters<typeof updateStory>[2] = {
      title: formTitle.value.trim() || null,
      body: formBody.value.trim(),
      narrative_time: formNarrativeTime.value.trim() || null,
      person_ids: formPersonIds.value,
      version: formVersion.value,
    }
    if (personRefsChanged) {
      updateData.person_refs = formPersonRefs.value
    }
    const updatedStory = await updateStory(
      familyId.value,
      selectedStory.value.id,
      updateData,
    )
    stories.value = stories.value.map((s) =>
      s.id === updatedStory.id ? updatedStory : s,
    )
    selectedStory.value = updatedStory
    viewMode.value = 'detail'
    successMessage.value = '故事更新成功'
    closePersonRefDropdown()
    setTimeout(() => { successMessage.value = '' }, 3000)
  } catch (err) {
    if (err instanceof StoryApiError) {
      if (err.code === 'VERSION_CONFLICT' && err.conflictStory) {
        formTitle.value = err.conflictStory.title || ''
        formBody.value = err.conflictStory.body
        formNarrativeTime.value = err.conflictStory.narrative_time || ''
        formPersonIds.value = [...err.conflictStory.person_ids]
        formVersion.value = err.conflictStory.version
        const conflictRefs = err.conflictStory.person_refs
          ?.filter((r): r is PersonRefResponse & { person_id: string } => r.person_id !== null && r.clickable)
          .map((r) => ({
            person_id: r.person_id,
            display_name_snapshot: r.display_name_snapshot,
          })) ?? []
        formPersonRefs.value = [...conflictRefs]
        formPersonRefsOriginal.value = [...conflictRefs]
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

function handleBodyInput(event: Event) {
  const textarea = event.target as HTMLTextAreaElement
  const value = textarea.value
  const cursorPos = textarea.selectionStart

  const textBeforeCursor = value.slice(0, cursorPos)
  const hashMatch = textBeforeCursor.match(/#([^#@\s]*)$/)

  if (hashMatch) {
    personRefFilterText.value = hashMatch[1]
    showPersonRefDropdown.value = true

    const rect = textarea.getBoundingClientRect()
    const lineHeight = parseInt(getComputedStyle(textarea).lineHeight) || 20
    const lines = textBeforeCursor.split('\n')
    const currentLineIndex = lines.length - 1
    
    personRefDropdownPosition.value = {
      top: rect.top + (currentLineIndex + 1) * lineHeight + 4,
      left: rect.left + 12,
    }
  } else {
    showPersonRefDropdown.value = false
    personRefFilterText.value = ''
  }
}

function handleBodyKeydown(event: KeyboardEvent) {
  if (showPersonRefDropdown.value) {
    if (event.key === 'Escape') {
      showPersonRefDropdown.value = false
      event.preventDefault()
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
    } else if (event.key === 'Enter' && filteredPersonRefCandidates.value.length > 0) {
      selectPersonRef(filteredPersonRefCandidates.value[0])
      event.preventDefault()
    }
  }
}

function selectPersonRef(candidate: PersonRefCandidate) {
  const displayName = candidate.display_name
  const refText = `#${displayName} `
  const refReq: PersonRefRequest = {
    person_id: candidate.person_id,
    display_name_snapshot: displayName,
  }

  const textarea = formBodyTextareaRef.value
  if (textarea) {
    const value = formBody.value
    const cursorPos = textarea.selectionStart
    const textBeforeCursor = value.slice(0, cursorPos)
    const hashIndex = textBeforeCursor.lastIndexOf('#')
    if (hashIndex !== -1) {
      formBody.value = value.slice(0, hashIndex) + refText + value.slice(cursorPos)
      if (!formPersonRefs.value.some((r) => r.person_id === candidate.person_id)) {
        formPersonRefs.value.push(refReq)
      }
      nextTick(() => {
        const newCursorPos = hashIndex + refText.length
        textarea.selectionStart = newCursorPos
        textarea.selectionEnd = newCursorPos
        textarea.focus()
      })
    }
  }

  showPersonRefDropdown.value = false
  personRefFilterText.value = ''
}

function openPersonRefPicker() {
  showPersonRefDropdown.value = true
  personRefFilterText.value = ''
  
  const textarea = formBodyTextareaRef.value
  if (textarea) {
    const rect = textarea.getBoundingClientRect()
    personRefDropdownPosition.value = {
      top: rect.bottom + 4,
      left: rect.left,
    }
  }
}

function addPersonRefFromPicker(candidate: PersonRefCandidate) {
  const displayName = candidate.display_name
  const refReq: PersonRefRequest = {
    person_id: candidate.person_id,
    display_name_snapshot: displayName,
  }

  if (!formPersonRefs.value.some((r) => r.person_id === candidate.person_id)) {
    formPersonRefs.value.push(refReq)
  }

  const textarea = formBodyTextareaRef.value
  if (textarea) {
    const cursorPos = textarea.selectionStart
    const insertText = `#${displayName} `
    formBody.value = formBody.value.slice(0, cursorPos) + insertText + formBody.value.slice(cursorPos)
    nextTick(() => {
      const newCursorPos = cursorPos + insertText.length
      textarea.selectionStart = newCursorPos
      textarea.selectionEnd = newCursorPos
      textarea.focus()
    })
  }

  showPersonRefDropdown.value = false
}

function removePersonRef(personId: string) {
  formPersonRefs.value = formPersonRefs.value.filter((r) => r.person_id !== personId)
}

function closePersonRefDropdown() {
  showPersonRefDropdown.value = false
  personRefFilterText.value = ''
}

function havePersonRefsChanged(): boolean {
  const current = formPersonRefs.value
  const original = formPersonRefsOriginal.value
  if (current.length !== original.length) return true
  const currentIds = new Set(current.map((r) => r.person_id))
  const originalIds = new Set(original.map((r) => r.person_id))
  if (currentIds.size !== originalIds.size) return true
  for (const id of currentIds) {
    if (!originalIds.has(id)) return true
  }
  return false
}

function handlePersonRefClick(personRef: PersonRefResponse) {
  if (!personRef.clickable || !personRef.person_id) return
  
  if (familyId.value) {
    router.push({
      name: 'tree',
      query: {
        familyId: familyId.value,
        selectPerson: personRef.person_id,
      },
    })
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

          <div v-if="selectedStory.person_refs && selectedStory.person_refs.length > 0" class="detail-person-refs">
            <span class="refs-label">引用人物：</span>
            <template v-for="(ref, idx) in selectedStory.person_refs" :key="idx">
              <button
                v-if="ref.clickable"
                type="button"
                class="person-ref-display person-ref-active"
                @click="handlePersonRefClick(ref)"
              >
                #{{ ref.display_name_snapshot }}
              </button>
              <span
                v-else
                class="person-ref-display person-ref-inactive"
                :class="{ 'person-ref-deceased': ref.status === 'deleted' }"
              >
                #{{ ref.display_name_snapshot }}
              </span>
            </template>
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
                ref="formBodyTextareaRef"
                v-model="formBody"
                class="form-textarea"
                placeholder="写下家族故事...（输入 # 可插入人物引用）"
                rows="10"
                :disabled="submitting"
                @input="handleBodyInput"
                @keydown="handleBodyKeydown"
              ></textarea>
              <div class="body-actions">
                <button
                  type="button"
                  class="btn-insert-person"
                  :disabled="submitting"
                  @click="openPersonRefPicker"
                >
                  插入人物
                </button>
                <span class="form-hint" :class="{ 'hint-error': isBodyOverLimit }">
                  {{ bodyCharCount }} / {{ STORY_BODY_MAX_LENGTH }} 字
                </span>
              </div>
              <div v-if="formPersonRefs.length > 0" class="person-ref-chips">
                <span
                  v-for="r in formPersonRefs"
                  :key="r.person_id"
                  class="person-ref-chip"
                >
                  #{{ r.display_name_snapshot }}
                  <button
                    type="button"
                    class="chip-remove"
                    :disabled="submitting"
                    @click="removePersonRef(r.person_id)"
                  >
                    ×
                  </button>
                </span>
              </div>
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

      <!-- Person Ref Dropdown -->
      <div
        v-if="showPersonRefDropdown && filteredPersonRefCandidates.length > 0"
        class="person-ref-dropdown"
        :style="{ top: personRefDropdownPosition.top + 'px', left: personRefDropdownPosition.left + 'px' }"
      >
        <div class="person-ref-dropdown-header">选择要引用的人物</div>
        <ul class="person-ref-dropdown-list">
          <li
            v-for="candidate in filteredPersonRefCandidates"
            :key="candidate.person_id"
            class="person-ref-dropdown-item"
            :class="{ 'candidate-deceased': candidate.deceased }"
            @click="addPersonRefFromPicker(candidate)"
          >
            {{ candidate.display_name }}
            <span v-if="candidate.deceased" class="deceased-badge">已故</span>
          </li>
        </ul>
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

/* Person Ref styles */
.detail-person-refs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-top: 1px solid var(--color-border, #e8e6e2);
}

.refs-label {
  font-size: 14px;
  color: #666;
  margin-right: 4px;
}

.person-ref-display {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 13px;
  font-family: inherit;
}

.person-ref-active {
  background: #e8f4ed;
  color: #276749;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.person-ref-active:hover {
  background: #d4e9dc;
  border-color: #276749;
}

.person-ref-inactive {
  background: #f0eeeb;
  color: #888;
  cursor: default;
}

.person-ref-deceased {
  text-decoration: line-through;
  opacity: 0.7;
}

.body-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}

.btn-insert-person {
  padding: 6px 12px;
  border: 1px solid var(--color-accent, #2f5d50);
  border-radius: 6px;
  background: #fff;
  color: var(--color-accent, #2f5d50);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-insert-person:hover:not(:disabled) {
  background: rgba(47, 93, 80, 0.08);
}

.btn-insert-person:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.person-ref-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.person-ref-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: #e8f4ed;
  border-radius: 4px;
  font-size: 13px;
  color: #276749;
}

.chip-remove {
  padding: 0 2px;
  margin-left: 2px;
  border: none;
  background: transparent;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  line-height: 1;
}

.chip-remove:hover {
  color: #c53030;
}

.chip-remove:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.person-ref-dropdown {
  position: fixed;
  z-index: 100;
  min-width: 200px;
  max-width: 300px;
  max-height: 250px;
  background: #fff;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.person-ref-dropdown-header {
  padding: 8px 12px;
  font-size: 12px;
  color: #666;
  background: #faf9f7;
  border-bottom: 1px solid var(--color-border, #e8e6e2);
}

.person-ref-dropdown-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 210px;
  overflow-y: auto;
}

.person-ref-dropdown-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  font-size: 14px;
  color: #333;
  cursor: pointer;
}

.person-ref-dropdown-item:hover {
  background: #f5f5f5;
}

.candidate-deceased {
  color: #666;
}

.deceased-badge {
  font-size: 11px;
  padding: 2px 6px;
  background: #f0eeeb;
  border-radius: 4px;
  color: #888;
}
</style>
