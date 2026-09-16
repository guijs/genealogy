<script setup lang="ts">
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import {
  listComments,
  createComment,
  updateComment,
  deleteComment,
  CommentApiError,
  COMMENT_BODY_MAX_LENGTH,
} from '../api/commentClient'
import {
  fetchPersonRefCandidates,
  PersonRefApiError,
} from '../api/personRefClient'
import type {
  CommentResponse,
  MentionRequest,
  MentionResponse,
  PersonRefRequest,
  PersonRefResponse,
  PersonRefCandidate,
} from '../api/types'
import type { FamilyMember } from '../api/memberClient'

const props = defineProps<{
  familyId: string
  storyId: string
  currentUserId: string
  members: FamilyMember[]
  isAdmin: boolean
  canWrite: boolean
}>()

const emit = defineEmits<{
  (e: 'error', message: string): void
  (e: 'mentionClick', userId: string): void
  (e: 'personRefClick', personId: string): void
}>()

const comments = ref<CommentResponse[]>([])
const loading = ref(false)
const commentsError = ref('')

const newCommentBody = ref('')
const submitting = ref(false)

const editingCommentId = ref<string | null>(null)
const editBody = ref('')
const editUpdatedAt = ref('')

const deleteConfirmId = ref<string | null>(null)
const deleting = ref(false)

const newMentions = ref<MentionRequest[]>([])
const editMentions = ref<MentionRequest[]>([])
const editMentionsOriginal = ref<MentionRequest[]>([])

const clickedMentionKey = ref<string | null>(null)
const clickedPersonRefKey = ref<string | null>(null)

const showMentionDropdown = ref(false)
const mentionDropdownMode = ref<'new' | 'edit'>('new')
const mentionFilterText = ref('')
const mentionDropdownPosition = ref({ top: 0, left: 0 })
const newTextareaRef = ref<HTMLTextAreaElement | null>(null)
const editTextareaRef = ref<HTMLTextAreaElement | null>(null)

const personRefCandidates = ref<PersonRefCandidate[]>([])
const newPersonRefs = ref<PersonRefRequest[]>([])
const editPersonRefs = ref<PersonRefRequest[]>([])
const editPersonRefsOriginal = ref<PersonRefRequest[]>([])
const showPersonRefDropdown = ref(false)
const personRefDropdownMode = ref<'new' | 'edit'>('new')
const personRefFilterText = ref('')
const personRefDropdownPosition = ref({ top: 0, left: 0 })

const filteredMembers = computed(() => {
  const filter = mentionFilterText.value.toLowerCase()
  return props.members.filter((m) => {
    const display = getMemberDisplayName(m.user_id).toLowerCase()
    return display.includes(filter)
  })
})

const filteredPersonRefCandidates = computed(() => {
  const filter = personRefFilterText.value.toLowerCase()
  const mode = personRefDropdownMode.value
  const selectedIds = new Set(
    mode === 'new'
      ? newPersonRefs.value.map((r) => r.person_id)
      : editPersonRefs.value.map((r) => r.person_id)
  )
  return personRefCandidates.value.filter((c) => {
    if (selectedIds.has(c.person_id)) return false
    return c.display_name.toLowerCase().includes(filter)
  })
})

const newCommentCharCount = computed(() => newCommentBody.value.length)
const isNewCommentOverLimit = computed(() => newCommentBody.value.length > COMMENT_BODY_MAX_LENGTH)
const canSubmitNewComment = computed(() => {
  const body = newCommentBody.value.trim()
  return body.length > 0 && !isNewCommentOverLimit.value && !submitting.value
})

const editCommentCharCount = computed(() => editBody.value.length)
const isEditOverLimit = computed(() => editBody.value.length > COMMENT_BODY_MAX_LENGTH)
const canSubmitEdit = computed(() => {
  const body = editBody.value.trim()
  return body.length > 0 && !isEditOverLimit.value && !submitting.value
})

function getMemberRole(userId: string): string | null {
  const member = props.members.find((m) => m.user_id === userId)
  return member?.role ?? null
}

function getMemberDisplayName(userId: string): string {
  const member = props.members.find((m) => m.user_id === userId)
  if (member) {
    const roleLabel = member.role === 'admin' ? '管理员' : member.role === 'editor' ? '编辑' : '查看者'
    return `成员 ${userId.slice(0, 8)}（${roleLabel}）`
  }
  return `成员 ${userId.slice(0, 8)}`
}

function getAuthorDisplay(userId: string): string {
  const member = props.members.find((m) => m.user_id === userId)
  if (member) {
    const roleLabel = member.role === 'admin' ? '管理员' : member.role === 'editor' ? '编辑' : '查看者'
    return `${userId.slice(0, 8)}...（${roleLabel}）`
  }
  return userId.slice(0, 8) + '...'
}

async function loadPersonRefCandidates() {
  if (!props.familyId) return

  try {
    const result = await fetchPersonRefCandidates(props.familyId)
    personRefCandidates.value = result.candidates
  } catch (e) {
    if (e instanceof PersonRefApiError) {
      console.warn('Failed to load person ref candidates:', e.message)
    } else {
      console.warn('Failed to load person ref candidates:', e)
    }
  }
}

function handleTextareaInput(event: Event, mode: 'new' | 'edit') {
  const textarea = event.target as HTMLTextAreaElement
  const value = textarea.value
  const cursorPos = textarea.selectionStart

  const textBeforeCursor = value.slice(0, cursorPos)
  
  const atMatch = textBeforeCursor.match(/@([^@#\s]*)$/)
  const hashMatch = textBeforeCursor.match(/#([^@#\s]*)$/)

  if (atMatch) {
    mentionFilterText.value = atMatch[1]
    mentionDropdownMode.value = mode
    showMentionDropdown.value = true
    showPersonRefDropdown.value = false

    const rect = textarea.getBoundingClientRect()
    const lineHeight = parseInt(getComputedStyle(textarea).lineHeight) || 20
    const lines = textBeforeCursor.split('\n')
    const currentLineIndex = lines.length - 1
    
    mentionDropdownPosition.value = {
      top: rect.top + (currentLineIndex + 1) * lineHeight + 4,
      left: rect.left + 12,
    }
  } else if (hashMatch) {
    personRefFilterText.value = hashMatch[1]
    personRefDropdownMode.value = mode
    showPersonRefDropdown.value = true
    showMentionDropdown.value = false

    const rect = textarea.getBoundingClientRect()
    const lineHeight = parseInt(getComputedStyle(textarea).lineHeight) || 20
    const lines = textBeforeCursor.split('\n')
    const currentLineIndex = lines.length - 1
    
    personRefDropdownPosition.value = {
      top: rect.top + (currentLineIndex + 1) * lineHeight + 4,
      left: rect.left + 12,
    }
  } else {
    showMentionDropdown.value = false
    showPersonRefDropdown.value = false
    mentionFilterText.value = ''
    personRefFilterText.value = ''
  }
}

function handleTextareaKeydown(event: KeyboardEvent) {
  if (showMentionDropdown.value) {
    if (event.key === 'Escape') {
      showMentionDropdown.value = false
      event.preventDefault()
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
    } else if (event.key === 'Enter' && filteredMembers.value.length > 0) {
      selectMention(filteredMembers.value[0])
      event.preventDefault()
    }
  } else if (showPersonRefDropdown.value) {
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

function selectMention(member: FamilyMember) {
  const displayName = getMemberDisplayName(member.user_id)
  const mentionText = `@${displayName} `
  const mentionReq: MentionRequest = {
    user_id: member.user_id,
    display_name_snapshot: displayName,
  }

  if (mentionDropdownMode.value === 'new') {
    const textarea = newTextareaRef.value
    if (textarea) {
      const value = newCommentBody.value
      const cursorPos = textarea.selectionStart
      const textBeforeCursor = value.slice(0, cursorPos)
      const atIndex = textBeforeCursor.lastIndexOf('@')
      if (atIndex !== -1) {
        newCommentBody.value = value.slice(0, atIndex) + mentionText + value.slice(cursorPos)
        if (!newMentions.value.some((m) => m.user_id === member.user_id)) {
          newMentions.value.push(mentionReq)
        }
        nextTick(() => {
          const newCursorPos = atIndex + mentionText.length
          textarea.selectionStart = newCursorPos
          textarea.selectionEnd = newCursorPos
          textarea.focus()
        })
      }
    }
  } else {
    const textarea = editTextareaRef.value
    if (textarea) {
      const value = editBody.value
      const cursorPos = textarea.selectionStart
      const textBeforeCursor = value.slice(0, cursorPos)
      const atIndex = textBeforeCursor.lastIndexOf('@')
      if (atIndex !== -1) {
        editBody.value = value.slice(0, atIndex) + mentionText + value.slice(cursorPos)
        if (!editMentions.value.some((m) => m.user_id === member.user_id)) {
          editMentions.value.push(mentionReq)
        }
        nextTick(() => {
          const newCursorPos = atIndex + mentionText.length
          textarea.selectionStart = newCursorPos
          textarea.selectionEnd = newCursorPos
          textarea.focus()
        })
      }
    }
  }

  showMentionDropdown.value = false
  mentionFilterText.value = ''
}

function removeMention(userId: string, mode: 'new' | 'edit') {
  if (mode === 'new') {
    newMentions.value = newMentions.value.filter((m) => m.user_id !== userId)
  } else {
    editMentions.value = editMentions.value.filter((m) => m.user_id !== userId)
  }
}

function closeMentionDropdown() {
  showMentionDropdown.value = false
  mentionFilterText.value = ''
}

function selectPersonRef(candidate: PersonRefCandidate) {
  const displayName = candidate.display_name
  const refText = `#${displayName} `
  const refReq: PersonRefRequest = {
    person_id: candidate.person_id,
    display_name_snapshot: displayName,
  }

  if (personRefDropdownMode.value === 'new') {
    const textarea = newTextareaRef.value
    if (textarea) {
      const value = newCommentBody.value
      const cursorPos = textarea.selectionStart
      const textBeforeCursor = value.slice(0, cursorPos)
      const hashIndex = textBeforeCursor.lastIndexOf('#')
      if (hashIndex !== -1) {
        newCommentBody.value = value.slice(0, hashIndex) + refText + value.slice(cursorPos)
        if (!newPersonRefs.value.some((r) => r.person_id === candidate.person_id)) {
          newPersonRefs.value.push(refReq)
        }
        nextTick(() => {
          const newCursorPos = hashIndex + refText.length
          textarea.selectionStart = newCursorPos
          textarea.selectionEnd = newCursorPos
          textarea.focus()
        })
      }
    }
  } else {
    const textarea = editTextareaRef.value
    if (textarea) {
      const value = editBody.value
      const cursorPos = textarea.selectionStart
      const textBeforeCursor = value.slice(0, cursorPos)
      const hashIndex = textBeforeCursor.lastIndexOf('#')
      if (hashIndex !== -1) {
        editBody.value = value.slice(0, hashIndex) + refText + value.slice(cursorPos)
        if (!editPersonRefs.value.some((r) => r.person_id === candidate.person_id)) {
          editPersonRefs.value.push(refReq)
        }
        nextTick(() => {
          const newCursorPos = hashIndex + refText.length
          textarea.selectionStart = newCursorPos
          textarea.selectionEnd = newCursorPos
          textarea.focus()
        })
      }
    }
  }

  showPersonRefDropdown.value = false
  personRefFilterText.value = ''
}

function openPersonRefPicker(mode: 'new' | 'edit') {
  personRefDropdownMode.value = mode
  showPersonRefDropdown.value = true
  personRefFilterText.value = ''
  showMentionDropdown.value = false
  
  const textarea = mode === 'new' ? newTextareaRef.value : editTextareaRef.value
  if (textarea) {
    const rect = textarea.getBoundingClientRect()
    personRefDropdownPosition.value = {
      top: rect.bottom + 4,
      left: rect.left,
    }
  }
}

function addPersonRefFromPicker(candidate: PersonRefCandidate, mode: 'new' | 'edit') {
  const displayName = candidate.display_name
  const refReq: PersonRefRequest = {
    person_id: candidate.person_id,
    display_name_snapshot: displayName,
  }

  if (mode === 'new') {
    if (!newPersonRefs.value.some((r) => r.person_id === candidate.person_id)) {
      newPersonRefs.value.push(refReq)
    }

    const textarea = newTextareaRef.value
    if (textarea) {
      const cursorPos = textarea.selectionStart
      const insertText = `#${displayName} `
      newCommentBody.value = newCommentBody.value.slice(0, cursorPos) + insertText + newCommentBody.value.slice(cursorPos)
      nextTick(() => {
        const newCursorPos = cursorPos + insertText.length
        textarea.selectionStart = newCursorPos
        textarea.selectionEnd = newCursorPos
        textarea.focus()
      })
    }
  } else {
    if (!editPersonRefs.value.some((r) => r.person_id === candidate.person_id)) {
      editPersonRefs.value.push(refReq)
    }

    const textarea = editTextareaRef.value
    if (textarea) {
      const cursorPos = textarea.selectionStart
      const insertText = `#${displayName} `
      editBody.value = editBody.value.slice(0, cursorPos) + insertText + editBody.value.slice(cursorPos)
      nextTick(() => {
        const newCursorPos = cursorPos + insertText.length
        textarea.selectionStart = newCursorPos
        textarea.selectionEnd = newCursorPos
        textarea.focus()
      })
    }
  }

  showPersonRefDropdown.value = false
}

function removePersonRef(personId: string, mode: 'new' | 'edit') {
  if (mode === 'new') {
    newPersonRefs.value = newPersonRefs.value.filter((r) => r.person_id !== personId)
  } else {
    editPersonRefs.value = editPersonRefs.value.filter((r) => r.person_id !== personId)
  }
}

function closePersonRefDropdown() {
  showPersonRefDropdown.value = false
  personRefFilterText.value = ''
}

function havePersonRefsChanged(): boolean {
  const current = editPersonRefs.value
  const original = editPersonRefsOriginal.value
  if (current.length !== original.length) return true
  const currentIds = new Set(current.map((r) => r.person_id))
  const originalIds = new Set(original.map((r) => r.person_id))
  if (currentIds.size !== originalIds.size) return true
  for (const id of currentIds) {
    if (!originalIds.has(id)) return true
  }
  return false
}

function handlePersonRefClick(personRef: PersonRefResponse, commentId: string, idx: number) {
  if (!personRef.clickable || personRef.person_id === null) return
  
  const key = `${commentId}-pref-${idx}`
  clickedPersonRefKey.value = key
  emit('personRefClick', personRef.person_id)
  
  setTimeout(() => {
    if (clickedPersonRefKey.value === key) {
      clickedPersonRefKey.value = null
    }
  }, 300)
}

function handleMentionClick(mention: MentionResponse, commentId: string, idx: number) {
  if (mention.status !== 'active' || mention.user_id === null) return
  
  const key = `${commentId}-${idx}`
  clickedMentionKey.value = key
  emit('mentionClick', mention.user_id)
  
  setTimeout(() => {
    if (clickedMentionKey.value === key) {
      clickedMentionKey.value = null
    }
  }, 300)
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function isEdited(comment: CommentResponse): boolean {
  return comment.updated_at !== comment.created_at
}

function canEditComment(comment: CommentResponse): boolean {
  return props.canWrite && comment.author_user_id === props.currentUserId
}

function canDeleteComment(comment: CommentResponse): boolean {
  if (!props.canWrite) return false
  if (comment.author_user_id === props.currentUserId) return true
  return props.isAdmin
}

async function loadComments() {
  if (!props.familyId || !props.storyId) return

  loading.value = true
  commentsError.value = ''

  try {
    const result = await listComments({
      familyId: props.familyId,
      storyId: props.storyId,
    })
    comments.value = result.comments
  } catch (err) {
    comments.value = []
    if (err instanceof CommentApiError) {
      commentsError.value = err.message
    } else {
      commentsError.value = '加载评论失败'
    }
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!canSubmitNewComment.value || !props.familyId || !props.storyId) return

  submitting.value = true

  try {
    const createData: Parameters<typeof createComment>[2] = {
      body: newCommentBody.value.trim(),
    }
    if (newMentions.value.length > 0) {
      createData.mentions = newMentions.value
    }
    if (newPersonRefs.value.length > 0) {
      createData.person_refs = newPersonRefs.value
    }
    const newComment = await createComment(props.familyId, props.storyId, createData)
    comments.value = [...comments.value, newComment]
    newCommentBody.value = ''
    newMentions.value = []
    newPersonRefs.value = []
    closePersonRefDropdown()
  } catch (err) {
    if (err instanceof CommentApiError) {
      emit('error', err.message)
    } else {
      emit('error', '发表评论失败')
    }
  } finally {
    submitting.value = false
  }
}

function startEdit(comment: CommentResponse) {
  editingCommentId.value = comment.id
  editBody.value = comment.body
  editUpdatedAt.value = comment.updated_at
  const activeMentions = comment.mentions
    ?.filter((m): m is MentionResponse & { user_id: string } => m.user_id !== null && m.status === 'active')
    .map((m) => ({
      user_id: m.user_id,
      display_name_snapshot: m.display_name_snapshot,
    })) ?? []
  editMentions.value = [...activeMentions]
  editMentionsOriginal.value = [...activeMentions]
  const activePersonRefs = comment.person_refs
    ?.filter((r): r is PersonRefResponse & { person_id: string } => r.person_id !== null && r.clickable)
    .map((r) => ({
      person_id: r.person_id,
      display_name_snapshot: r.display_name_snapshot,
    })) ?? []
  editPersonRefs.value = [...activePersonRefs]
  editPersonRefsOriginal.value = [...activePersonRefs]
  closePersonRefDropdown()
}

function cancelEdit() {
  editingCommentId.value = null
  editBody.value = ''
  editUpdatedAt.value = ''
  editMentions.value = []
  editMentionsOriginal.value = []
  editPersonRefs.value = []
  editPersonRefsOriginal.value = []
  showMentionDropdown.value = false
  closePersonRefDropdown()
}

function haveMentionsChanged(): boolean {
  const current = editMentions.value
  const original = editMentionsOriginal.value
  if (current.length !== original.length) return true
  const currentIds = new Set(current.map((m) => m.user_id))
  const originalIds = new Set(original.map((m) => m.user_id))
  if (currentIds.size !== originalIds.size) return true
  for (const id of currentIds) {
    if (!originalIds.has(id)) return true
  }
  return false
}

async function handleUpdate() {
  if (!canSubmitEdit.value || !editingCommentId.value || !props.familyId || !props.storyId) return

  submitting.value = true

  try {
    const mentionsChanged = haveMentionsChanged()
    const personRefsChanged = havePersonRefsChanged()
    const updatePayload: { body: string; updated_at: string; mentions?: MentionRequest[]; person_refs?: PersonRefRequest[] } = {
      body: editBody.value.trim(),
      updated_at: editUpdatedAt.value,
    }
    if (mentionsChanged) {
      updatePayload.mentions = editMentions.value
    }
    if (personRefsChanged) {
      updatePayload.person_refs = editPersonRefs.value
    }
    const updatedComment = await updateComment(
      props.familyId,
      props.storyId,
      editingCommentId.value,
      updatePayload,
    )
    comments.value = comments.value.map((c) =>
      c.id === updatedComment.id ? updatedComment : c,
    )
    cancelEdit()
  } catch (err) {
    if (err instanceof CommentApiError) {
      if (err.code === 'VERSION_CONFLICT' && err.conflictComment) {
        comments.value = comments.value.map((c) =>
          c.id === err.conflictComment!.id ? err.conflictComment! : c,
        )
        editBody.value = err.conflictComment.body
        editUpdatedAt.value = err.conflictComment.updated_at
        const conflictActiveMentions = err.conflictComment.mentions
          ?.filter((m): m is MentionResponse & { user_id: string } => m.user_id !== null && m.status === 'active')
          .map((m) => ({
            user_id: m.user_id,
            display_name_snapshot: m.display_name_snapshot,
          })) ?? []
        editMentions.value = [...conflictActiveMentions]
        editMentionsOriginal.value = [...conflictActiveMentions]
        const conflictActivePersonRefs = err.conflictComment.person_refs
          ?.filter((r): r is PersonRefResponse & { person_id: string } => r.person_id !== null && r.clickable)
          .map((r) => ({
            person_id: r.person_id,
            display_name_snapshot: r.display_name_snapshot,
          })) ?? []
        editPersonRefs.value = [...conflictActivePersonRefs]
        editPersonRefsOriginal.value = [...conflictActivePersonRefs]
        emit('error', '评论已被更新，已加载最新内容')
      } else {
        emit('error', err.message)
      }
    } else {
      emit('error', '更新评论失败')
    }
  } finally {
    submitting.value = false
  }
}

function openDeleteConfirm(commentId: string) {
  deleteConfirmId.value = commentId
}

function closeDeleteConfirm() {
  deleteConfirmId.value = null
}

async function handleDelete() {
  if (!deleteConfirmId.value || !props.familyId || !props.storyId) return

  const deletingId = deleteConfirmId.value
  deleting.value = true

  try {
    await deleteComment(props.familyId, props.storyId, deletingId)
    comments.value = comments.value.filter((c) => c.id !== deletingId)
    closeDeleteConfirm()
  } catch (err) {
    if (err instanceof CommentApiError) {
      emit('error', err.message)
    } else {
      emit('error', '删除评论失败')
    }
  } finally {
    deleting.value = false
  }
}

watch(
  () => [props.familyId, props.storyId],
  ([newFamilyId, newStoryId], oldValue) => {
    const [oldFamilyId, oldStoryId] = oldValue ?? []
    if (newFamilyId && newStoryId && (newFamilyId !== oldFamilyId || newStoryId !== oldStoryId)) {
      cancelEdit()
      closeDeleteConfirm()
      closeMentionDropdown()
      closePersonRefDropdown()
      newMentions.value = []
      newPersonRefs.value = []
      loadComments()
      loadPersonRefCandidates()
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  comments.value = []
  cancelEdit()
  closeDeleteConfirm()
  closeMentionDropdown()
  closePersonRefDropdown()
  newMentions.value = []
  newPersonRefs.value = []
})

defineExpose({
  loadComments,
})
</script>

<template>
  <div class="comments-panel">
    <h3 class="comments-title">评论</h3>

    <div v-if="commentsError" class="comments-error" role="alert">
      {{ commentsError }}
    </div>

    <div v-if="loading" class="comments-loading">加载评论中…</div>

    <div v-else-if="comments.length === 0" class="comments-empty">
      暂无评论
    </div>

    <ul v-else class="comments-list">
      <li v-for="comment in comments" :key="comment.id" class="comment-item">
        <template v-if="editingCommentId === comment.id">
          <div class="comment-edit-form">
            <textarea
              ref="editTextareaRef"
              v-model="editBody"
              class="comment-textarea"
              rows="3"
              :disabled="submitting"
              placeholder="编辑评论...（输入 @ 提及成员，# 引用人物）"
              @input="handleTextareaInput($event, 'edit')"
              @keydown="handleTextareaKeydown"
            ></textarea>
            <div class="textarea-actions">
              <button
                type="button"
                class="btn-insert-person-sm"
                :disabled="submitting"
                @click="openPersonRefPicker('edit')"
              >
                插入人物
              </button>
              <span class="char-hint" :class="{ 'hint-error': isEditOverLimit }">
                {{ editCommentCharCount }} / {{ COMMENT_BODY_MAX_LENGTH }} 字
              </span>
            </div>
            <div v-if="editMentions.length > 0" class="mention-chips">
              <span
                v-for="m in editMentions"
                :key="m.user_id"
                class="mention-chip"
              >
                @{{ m.display_name_snapshot }}
                <button
                  type="button"
                  class="chip-remove"
                  :disabled="submitting"
                  @click="removeMention(m.user_id, 'edit')"
                >
                  ×
                </button>
              </span>
            </div>
            <div v-if="editPersonRefs.length > 0" class="person-ref-chips">
              <span
                v-for="r in editPersonRefs"
                :key="r.person_id"
                class="person-ref-chip"
              >
                #{{ r.display_name_snapshot }}
                <button
                  type="button"
                  class="chip-remove"
                  :disabled="submitting"
                  @click="removePersonRef(r.person_id, 'edit')"
                >
                  ×
                </button>
              </span>
            </div>
            <div class="edit-actions">
              <button
                type="button"
                class="btn-primary-sm"
                :disabled="!canSubmitEdit"
                @click="handleUpdate"
              >
                {{ submitting ? '保存中…' : '保存' }}
              </button>
              <button
                type="button"
                class="btn-secondary-sm"
                :disabled="submitting"
                @click="cancelEdit"
              >
                取消
              </button>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="comment-header">
            <span class="comment-author">{{ getAuthorDisplay(comment.author_user_id) }}</span>
            <span class="comment-time">
              {{ formatTime(comment.created_at) }}
              <span v-if="isEdited(comment)" class="comment-edited">（已编辑）</span>
            </span>
          </div>
          <div class="comment-body">{{ comment.body }}</div>
          <div v-if="comment.mentions && comment.mentions.length > 0" class="comment-mentions">
            <template v-for="(mention, idx) in comment.mentions" :key="idx">
              <button
                v-if="mention.status === 'active' && mention.user_id !== null"
                type="button"
                class="mention-display mention-active"
                :class="{ 'mention-clicked': clickedMentionKey === `${comment.id}-${idx}` }"
                @click="handleMentionClick(mention, comment.id, idx)"
              >
                @{{ mention.display_name_snapshot }}
              </button>
              <span
                v-else
                class="mention-display mention-inactive"
              >
                @{{ mention.display_name_snapshot }}
              </span>
            </template>
          </div>
          <div v-if="comment.person_refs && comment.person_refs.length > 0" class="comment-person-refs">
            <template v-for="(pref, idx) in comment.person_refs" :key="idx">
              <button
                v-if="pref.clickable && pref.person_id !== null"
                type="button"
                class="person-ref-display person-ref-active"
                :class="{ 'person-ref-clicked': clickedPersonRefKey === `${comment.id}-pref-${idx}` }"
                @click="handlePersonRefClick(pref, comment.id, idx)"
              >
                #{{ pref.display_name_snapshot }}
              </button>
              <span
                v-else
                class="person-ref-display person-ref-inactive"
                :class="{ 'person-ref-deceased': pref.status === 'deleted' }"
              >
                #{{ pref.display_name_snapshot }}
              </span>
            </template>
          </div>
          <div class="comment-actions">
            <button
              v-if="canEditComment(comment)"
              type="button"
              class="btn-link"
              @click="startEdit(comment)"
            >
              编辑
            </button>
            <button
              v-if="canDeleteComment(comment)"
              type="button"
              class="btn-link btn-link-danger"
              @click="openDeleteConfirm(comment.id)"
            >
              删除
            </button>
          </div>
        </template>
      </li>
    </ul>

    <div v-if="canWrite" class="compose-section">
      <textarea
        ref="newTextareaRef"
        v-model="newCommentBody"
        class="comment-textarea"
        rows="3"
        :disabled="submitting"
        placeholder="写下你的评论...（输入 @ 提及成员，# 引用人物）"
        @input="handleTextareaInput($event, 'new')"
        @keydown="handleTextareaKeydown"
      ></textarea>
      <div class="textarea-actions">
        <button
          type="button"
          class="btn-insert-person-sm"
          :disabled="submitting"
          @click="openPersonRefPicker('new')"
        >
          插入人物
        </button>
        <span class="char-hint" :class="{ 'hint-error': isNewCommentOverLimit }">
          {{ newCommentCharCount }} / {{ COMMENT_BODY_MAX_LENGTH }} 字
        </span>
      </div>
      <div v-if="newMentions.length > 0" class="mention-chips">
        <span
          v-for="m in newMentions"
          :key="m.user_id"
          class="mention-chip"
        >
          @{{ m.display_name_snapshot }}
          <button
            type="button"
            class="chip-remove"
            :disabled="submitting"
            @click="removeMention(m.user_id, 'new')"
          >
            ×
          </button>
        </span>
      </div>
      <div v-if="newPersonRefs.length > 0" class="person-ref-chips">
        <span
          v-for="r in newPersonRefs"
          :key="r.person_id"
          class="person-ref-chip"
        >
          #{{ r.display_name_snapshot }}
          <button
            type="button"
            class="chip-remove"
            :disabled="submitting"
            @click="removePersonRef(r.person_id, 'new')"
          >
            ×
          </button>
        </span>
      </div>
      <button
        type="button"
        class="btn-primary"
        :disabled="!canSubmitNewComment"
        @click="handleCreate"
      >
        {{ submitting ? '发表中…' : '发表评论' }}
      </button>
    </div>

    <!-- Mention Dropdown -->
    <div
      v-if="showMentionDropdown && filteredMembers.length > 0"
      class="mention-dropdown"
      :style="{ top: mentionDropdownPosition.top + 'px', left: mentionDropdownPosition.left + 'px' }"
    >
      <div class="mention-dropdown-header">选择要提及的成员</div>
      <ul class="mention-dropdown-list">
        <li
          v-for="member in filteredMembers"
          :key="member.user_id"
          class="mention-dropdown-item"
          @click="selectMention(member)"
        >
          {{ getMemberDisplayName(member.user_id) }}
        </li>
      </ul>
    </div>

    <div v-if="deleteConfirmId" class="modal-overlay" @click.self="closeDeleteConfirm">
      <div class="modal-box">
        <h4 class="modal-title">确认删除</h4>
        <p class="modal-desc">确定要删除这条评论吗？此操作无法撤销。</p>
        <div class="modal-actions">
          <button
            type="button"
            class="btn-danger"
            :disabled="deleting"
            @click="handleDelete"
          >
            {{ deleting ? '删除中…' : '确认删除' }}
          </button>
          <button
            type="button"
            class="btn-secondary"
            :disabled="deleting"
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
          @click="addPersonRefFromPicker(candidate, personRefDropdownMode)"
        >
          {{ candidate.display_name }}
          <span v-if="candidate.deceased" class="deceased-badge">已故</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.comments-panel {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border, #d8d4cc);
}

.comments-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.comments-error {
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #fef0f0;
  border: 1px solid #f5c6cb;
  border-radius: 6px;
  color: #c53030;
  font-size: 14px;
}

.comments-loading,
.comments-empty {
  font-size: 14px;
  color: #666;
  text-align: center;
  padding: 16px 0;
}

.comments-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.comment-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border, #e8e6e2);
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.comment-author {
  font-size: 13px;
  font-weight: 500;
  color: #555;
}

.comment-time {
  font-size: 12px;
  color: #999;
}

.comment-edited {
  color: #888;
  font-style: italic;
}

.comment-body {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-actions {
  margin-top: 8px;
  display: flex;
  gap: 12px;
}

.comment-edit-form {
  padding: 8px 0;
}

.comment-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  box-sizing: border-box;
  resize: vertical;
  min-height: 80px;
  line-height: 1.5;
}

.comment-textarea:focus {
  outline: none;
  border-color: var(--color-accent, #2f5d50);
}

.comment-textarea:disabled {
  background: #f0eeeb;
  cursor: not-allowed;
}

.char-hint {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #888;
}

.char-hint.hint-error {
  color: #c53030;
}

.edit-actions {
  display: flex;
  gap: 8px;
}

.compose-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border, #e8e6e2);
}

.btn-link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-accent, #2f5d50);
  font-size: 13px;
  cursor: pointer;
}

.btn-link:hover {
  text-decoration: underline;
}

.btn-link-danger {
  color: #c53030;
}

.btn-primary,
.btn-primary-sm {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary-sm {
  padding: 6px 12px;
  font-size: 13px;
}

.btn-primary:hover:not(:disabled),
.btn-primary-sm:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled,
.btn-primary-sm:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.btn-secondary,
.btn-secondary-sm {
  padding: 8px 16px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: #fff;
  font-size: 14px;
  cursor: pointer;
}

.btn-secondary-sm {
  padding: 6px 12px;
  font-size: 13px;
}

.btn-secondary:hover,
.btn-secondary-sm:hover {
  background: #f5f5f5;
}

.btn-secondary:disabled,
.btn-secondary-sm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-danger {
  flex: 1;
  padding: 10px 16px;
  border: none;
  border-radius: 6px;
  background: #c53030;
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

.btn-danger:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-danger:disabled {
  background: #ccc;
  cursor: not-allowed;
}

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
  width: min(360px, 90%);
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.modal-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}

.modal-desc {
  margin: 0 0 16px;
  font-size: 14px;
  color: #555;
  line-height: 1.5;
}

.modal-actions {
  display: flex;
  gap: 10px;
}

.modal-actions .btn-secondary {
  flex: 1;
}

/* Mention styles */
.mention-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 8px 0;
}

.mention-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: #e8f0ed;
  border-radius: 4px;
  font-size: 13px;
  color: var(--color-accent, #2f5d50);
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

.comment-mentions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.mention-display {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
  font-family: inherit;
}

.mention-active {
  background: #e8f0ed;
  color: var(--color-accent, #2f5d50);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.mention-active:hover {
  background: #d4e6df;
  border-color: var(--color-accent, #2f5d50);
}

.mention-active:active,
.mention-active.mention-clicked {
  background: #c0dbd1;
}

.mention-inactive {
  background: #f0eeeb;
  color: #888;
  cursor: default;
}

.mention-dropdown {
  position: fixed;
  z-index: 100;
  min-width: 200px;
  max-width: 300px;
  max-height: 200px;
  background: #fff;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.mention-dropdown-header {
  padding: 8px 12px;
  font-size: 12px;
  color: #666;
  background: #faf9f7;
  border-bottom: 1px solid var(--color-border, #e8e6e2);
}

.mention-dropdown-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 160px;
  overflow-y: auto;
}

.mention-dropdown-item {
  padding: 10px 12px;
  font-size: 14px;
  color: #333;
  cursor: pointer;
}

.mention-dropdown-item:hover {
  background: #f5f5f5;
}

/* Person Ref styles */
.textarea-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.btn-insert-person-sm {
  padding: 4px 10px;
  border: 1px solid var(--color-accent, #2f5d50);
  border-radius: 4px;
  background: #fff;
  color: var(--color-accent, #2f5d50);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-insert-person-sm:hover:not(:disabled) {
  background: rgba(47, 93, 80, 0.08);
}

.btn-insert-person-sm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.comment-person-refs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.person-ref-display {
  display: inline-block;
  padding: 2px 8px;
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

.person-ref-active:active,
.person-ref-active.person-ref-clicked {
  background: #c0dbd1;
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

.person-ref-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 6px 0;
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

.person-ref-dropdown {
  position: fixed;
  z-index: 100;
  min-width: 200px;
  max-width: 300px;
  max-height: 200px;
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
  max-height: 160px;
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
