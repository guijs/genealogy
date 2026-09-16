/** @vitest-environment happy-dom */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import StoryCommentsPanel from './StoryCommentsPanel.vue'
import type { CommentResponse } from '../api/types'
import type { FamilyMember } from '../api/memberClient'

vi.mock('../api/commentClient', () => ({
  listComments: vi.fn(),
  createComment: vi.fn(),
  updateComment: vi.fn(),
  deleteComment: vi.fn(),
  CommentApiError: class CommentApiError extends Error {
    status: number
    code?: string
    constructor(message: string, status: number, code?: string) {
      super(message)
      this.status = status
      this.code = code
    }
  },
  COMMENT_BODY_MAX_LENGTH: 2000,
}))

import { listComments, deleteComment } from '../api/commentClient'

const mockComment = (overrides: Partial<CommentResponse> = {}): CommentResponse => ({
  id: 'comment-1',
  story_id: 'story-1',
  author_user_id: 'user-author',
  body: 'Test comment body',
  created_at: '2024-01-01T00:00:00Z',
  updated_at: '2024-01-01T00:00:00Z',
  ...overrides,
})

const mockMember = (overrides: Partial<FamilyMember> = {}): FamilyMember => ({
  user_id: 'user-1',
  family_id: 'family-1',
  role: 'viewer',
  joined_at: '2024-01-01T00:00:00Z',
  ...overrides,
})

describe('StoryCommentsPanel', () => {
  beforeEach(() => {
    vi.mocked(listComments).mockResolvedValue({ comments: [] })
    vi.mocked(deleteComment).mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('viewer (canWrite=false)', () => {
    it('does not show compose section', async () => {
      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-viewer',
          members: [mockMember({ user_id: 'user-viewer', role: 'viewer' })],
          isAdmin: false,
          canWrite: false,
        },
      })

      await flushPromises()

      expect(wrapper.find('.compose-section').exists()).toBe(false)
    })
  })

  describe('author with canWrite', () => {
    it('shows edit control for own comment', async () => {
      const ownComment = mockComment({ author_user_id: 'user-author' })
      vi.mocked(listComments).mockResolvedValue({ comments: [ownComment] })

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-author',
          members: [mockMember({ user_id: 'user-author', role: 'editor' })],
          isAdmin: false,
          canWrite: true,
        },
      })

      await flushPromises()

      const editButtons = wrapper.findAll('.btn-link').filter(btn => btn.text() === '编辑')
      expect(editButtons.length).toBe(1)
    })
  })

  describe('delete control visibility', () => {
    it('admin sees delete for others\' comments', async () => {
      const othersComment = mockComment({ author_user_id: 'user-other' })
      vi.mocked(listComments).mockResolvedValue({ comments: [othersComment] })

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-admin',
          members: [
            mockMember({ user_id: 'user-admin', role: 'admin' }),
            mockMember({ user_id: 'user-other', role: 'editor' }),
          ],
          isAdmin: true,
          canWrite: true,
        },
      })

      await flushPromises()

      const deleteButtons = wrapper.findAll('.btn-link-danger').filter(btn => btn.text() === '删除')
      expect(deleteButtons.length).toBe(1)
    })

    it('editor (non-admin) does not see delete for others\' comments', async () => {
      const othersComment = mockComment({ author_user_id: 'user-other' })
      vi.mocked(listComments).mockResolvedValue({ comments: [othersComment] })

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-editor',
          members: [
            mockMember({ user_id: 'user-editor', role: 'editor' }),
            mockMember({ user_id: 'user-other', role: 'editor' }),
          ],
          isAdmin: false,
          canWrite: true,
        },
      })

      await flushPromises()

      const deleteButtons = wrapper.findAll('.btn-link-danger').filter(btn => btn.text() === '删除')
      expect(deleteButtons.length).toBe(0)
    })
  })

  describe('delete success path', () => {
    it('captures comment id before clearing deleteConfirmId (no throw)', async () => {
      const commentToDelete = mockComment({ id: 'comment-to-delete', author_user_id: 'user-admin' })
      vi.mocked(listComments).mockResolvedValue({ comments: [commentToDelete] })
      vi.mocked(deleteComment).mockResolvedValue(undefined)

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-admin',
          members: [mockMember({ user_id: 'user-admin', role: 'admin' })],
          isAdmin: true,
          canWrite: true,
        },
      })

      await flushPromises()

      const deleteButton = wrapper.find('.btn-link-danger')
      expect(deleteButton.exists()).toBe(true)
      await deleteButton.trigger('click')

      await flushPromises()

      expect(wrapper.find('.modal-overlay').exists()).toBe(true)

      const confirmButton = wrapper.find('.btn-danger')
      await confirmButton.trigger('click')

      await flushPromises()

      expect(deleteComment).toHaveBeenCalledWith('family-1', 'story-1', 'comment-to-delete')

      expect(wrapper.find('.modal-overlay').exists()).toBe(false)
      expect(wrapper.findAll('.comment-item').length).toBe(0)
    })
  })
})
