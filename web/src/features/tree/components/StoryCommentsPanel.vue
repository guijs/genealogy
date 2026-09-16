<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import {
  listComments,
  createComment,
  updateComment,
  deleteComment,
  CommentApiError,
  COMMENT_BODY_MAX_LENGTH,
} from '../api/commentClient'
import type { CommentResponse } from '../api/types'
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

function getAuthorDisplay(userId: string): string {
  const member = props.members.find((m) => m.user_id === userId)
  if (member) {
    const roleLabel = member.role === 'admin' ? '管理员' : member.role === 'editor' ? '编辑' : '查看者'
    return `${userId.slice(0, 8)}...（${roleLabel}）`
  }
  return userId.slice(0, 8) + '...'
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
    const newComment = await createComment(props.familyId, props.storyId, {
      body: newCommentBody.value.trim(),
    })
    comments.value = [...comments.value, newComment]
    newCommentBody.value = ''
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
}

function cancelEdit() {
  editingCommentId.value = null
  editBody.value = ''
  editUpdatedAt.value = ''
}

async function handleUpdate() {
  if (!canSubmitEdit.value || !editingCommentId.value || !props.familyId || !props.storyId) return

  submitting.value = true

  try {
    const updatedComment = await updateComment(
      props.familyId,
      props.storyId,
      editingCommentId.value,
      {
        body: editBody.value.trim(),
        updated_at: editUpdatedAt.value,
      },
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
  ([newFamilyId, newStoryId], [oldFamilyId, oldStoryId]) => {
    if (newFamilyId && newStoryId && (newFamilyId !== oldFamilyId || newStoryId !== oldStoryId)) {
      cancelEdit()
      closeDeleteConfirm()
      loadComments()
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  comments.value = []
  cancelEdit()
  closeDeleteConfirm()
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
              v-model="editBody"
              class="comment-textarea"
              rows="3"
              :disabled="submitting"
              placeholder="编辑评论..."
            ></textarea>
            <p class="char-hint" :class="{ 'hint-error': isEditOverLimit }">
              {{ editCommentCharCount }} / {{ COMMENT_BODY_MAX_LENGTH }} 字
            </p>
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
        v-model="newCommentBody"
        class="comment-textarea"
        rows="3"
        :disabled="submitting"
        placeholder="写下你的评论..."
      ></textarea>
      <p class="char-hint" :class="{ 'hint-error': isNewCommentOverLimit }">
        {{ newCommentCharCount }} / {{ COMMENT_BODY_MAX_LENGTH }} 字
      </p>
      <button
        type="button"
        class="btn-primary"
        :disabled="!canSubmitNewComment"
        @click="handleCreate"
      >
        {{ submitting ? '发表中…' : '发表评论' }}
      </button>
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
</style>
