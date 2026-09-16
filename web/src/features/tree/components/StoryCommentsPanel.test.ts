/** @vitest-environment happy-dom */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import StoryCommentsPanel from './StoryCommentsPanel.vue'
import type { CommentResponse, MentionResponse } from '../api/types'
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

import { listComments, createComment, updateComment, deleteComment } from '../api/commentClient'

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

  describe('mention display', () => {
    it('displays active mentions as clickable buttons with highlight styling', async () => {
      const activeMention: MentionResponse = {
        user_id: 'user-mentioned',
        display_name_snapshot: '成员 user-men（编辑）',
        status: 'active',
      }
      const commentWithMention = mockComment({
        mentions: [activeMention],
      })
      vi.mocked(listComments).mockResolvedValue({ comments: [commentWithMention] })

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

      const mentionDisplay = wrapper.find('.mention-active')
      expect(mentionDisplay.exists()).toBe(true)
      expect(mentionDisplay.element.tagName.toLowerCase()).toBe('button')
      expect(mentionDisplay.text()).toContain('@成员 user-men（编辑）')
    })

    it('emits mentionClick event when active mention is clicked', async () => {
      const activeMention: MentionResponse = {
        user_id: 'user-mentioned',
        display_name_snapshot: '成员 user-men（编辑）',
        status: 'active',
      }
      const commentWithMention = mockComment({
        mentions: [activeMention],
      })
      vi.mocked(listComments).mockResolvedValue({ comments: [commentWithMention] })

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

      const mentionButton = wrapper.find('.mention-active')
      await mentionButton.trigger('click')

      expect(wrapper.emitted('mentionClick')).toBeTruthy()
      expect(wrapper.emitted('mentionClick')![0]).toEqual(['user-mentioned'])
    })

    it('displays left/removed mentions as non-clickable spans with muted styling', async () => {
      const leftMention: MentionResponse = {
        user_id: null,
        display_name_snapshot: '成员 user-lef（已离开）',
        status: 'left',
      }
      const removedMention: MentionResponse = {
        user_id: 'user-removed',
        display_name_snapshot: '成员 user-rem（已移除）',
        status: 'removed',
      }
      const commentWithMentions = mockComment({
        mentions: [leftMention, removedMention],
      })
      vi.mocked(listComments).mockResolvedValue({ comments: [commentWithMentions] })

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

      const inactiveMentions = wrapper.findAll('.mention-inactive')
      expect(inactiveMentions.length).toBe(2)
      expect(inactiveMentions[0].element.tagName.toLowerCase()).toBe('span')
      expect(inactiveMentions[1].element.tagName.toLowerCase()).toBe('span')
      expect(inactiveMentions[0].text()).toContain('@成员 user-lef（已离开）')
      expect(inactiveMentions[1].text()).toContain('@成员 user-rem（已移除）')
    })

    it('shows mention picker placeholder in compose textarea', async () => {
      vi.mocked(listComments).mockResolvedValue({ comments: [] })

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-editor',
          members: [mockMember({ user_id: 'user-editor', role: 'editor' })],
          isAdmin: false,
          canWrite: true,
        },
      })

      await flushPromises()

      const textarea = wrapper.find('.compose-section textarea')
      expect(textarea.exists()).toBe(true)
      expect(textarea.attributes('placeholder')).toContain('@')
    })

    it('creates comment with mentions in request', async () => {
      vi.mocked(listComments).mockResolvedValue({ comments: [] })
      vi.mocked(createComment).mockResolvedValue(mockComment())

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-editor',
          members: [
            mockMember({ user_id: 'user-editor', role: 'editor' }),
            mockMember({ user_id: 'user-other', role: 'viewer' }),
          ],
          isAdmin: false,
          canWrite: true,
        },
      })

      await flushPromises()

      const textarea = wrapper.find('.compose-section textarea')
      await textarea.setValue('Test comment')

      const submitBtn = wrapper.find('.compose-section .btn-primary')
      await submitBtn.trigger('click')

      await flushPromises()

      expect(createComment).toHaveBeenCalledWith(
        'family-1',
        'story-1',
        expect.objectContaining({
          body: 'Test comment',
        }),
      )
    })
  })

  describe('mentions update strategy (SoT)', () => {
    it('omits mentions from update payload when mentions unchanged', async () => {
      const activeMention: MentionResponse = {
        user_id: 'user-mentioned',
        display_name_snapshot: '成员 user-men（编辑）',
        status: 'active',
      }
      const commentWithMention = mockComment({
        author_user_id: 'user-editor',
        mentions: [activeMention],
      })
      vi.mocked(listComments).mockResolvedValue({ comments: [commentWithMention] })
      vi.mocked(updateComment).mockResolvedValue({
        ...commentWithMention,
        body: 'Updated body',
        updated_at: '2024-01-02T00:00:00Z',
      })

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-editor',
          members: [
            mockMember({ user_id: 'user-editor', role: 'editor' }),
            mockMember({ user_id: 'user-mentioned', role: 'viewer' }),
          ],
          isAdmin: false,
          canWrite: true,
        },
      })

      await flushPromises()

      const editButton = wrapper.find('.btn-link')
      await editButton.trigger('click')
      await flushPromises()

      const editTextarea = wrapper.find('.comment-edit-form textarea')
      await editTextarea.setValue('Updated body')

      const saveButton = wrapper.find('.btn-primary-sm')
      await saveButton.trigger('click')
      await flushPromises()

      expect(updateComment).toHaveBeenCalledWith(
        'family-1',
        'story-1',
        'comment-1',
        expect.objectContaining({
          body: 'Updated body',
        }),
      )
      const updatePayload = vi.mocked(updateComment).mock.calls[0][3]
      expect(updatePayload).not.toHaveProperty('mentions')
    })

    it('includes mentions in update payload when mentions changed (added)', async () => {
      const commentNoMentions = mockComment({
        author_user_id: 'user-editor',
        mentions: [],
      })
      vi.mocked(listComments).mockResolvedValue({ comments: [commentNoMentions] })
      vi.mocked(updateComment).mockResolvedValue({
        ...commentNoMentions,
        body: 'Hello @成员 user-oth（查看者）',
        updated_at: '2024-01-02T00:00:00Z',
        mentions: [{ user_id: 'user-other', display_name_snapshot: '成员 user-oth（查看者）', status: 'active' as const }],
      })

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-editor',
          members: [
            mockMember({ user_id: 'user-editor', role: 'editor' }),
            mockMember({ user_id: 'user-other', role: 'viewer' }),
          ],
          isAdmin: false,
          canWrite: true,
        },
      })

      await flushPromises()

      const editButton = wrapper.find('.btn-link')
      await editButton.trigger('click')
      await flushPromises()

      const vm = wrapper.vm as unknown as { editMentions: { user_id: string; display_name_snapshot: string }[] }
      vm.editMentions.push({ user_id: 'user-other', display_name_snapshot: '成员 user-oth（查看者）' })

      const editTextarea = wrapper.find('.comment-edit-form textarea')
      await editTextarea.setValue('Hello @成员 user-oth（查看者）')

      const saveButton = wrapper.find('.btn-primary-sm')
      await saveButton.trigger('click')
      await flushPromises()

      expect(updateComment).toHaveBeenCalled()
      const updatePayload = vi.mocked(updateComment).mock.calls[0][3]
      expect(updatePayload).toHaveProperty('mentions')
      expect(updatePayload.mentions).toEqual([{ user_id: 'user-other', display_name_snapshot: '成员 user-oth（查看者）' }])
    })

    it('includes empty mentions array when all mentions removed', async () => {
      const activeMention: MentionResponse = {
        user_id: 'user-mentioned',
        display_name_snapshot: '成员 user-men（编辑）',
        status: 'active',
      }
      const commentWithMention = mockComment({
        author_user_id: 'user-editor',
        mentions: [activeMention],
      })
      vi.mocked(listComments).mockResolvedValue({ comments: [commentWithMention] })
      vi.mocked(updateComment).mockResolvedValue({
        ...commentWithMention,
        body: 'No mentions now',
        updated_at: '2024-01-02T00:00:00Z',
        mentions: [],
      })

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-editor',
          members: [
            mockMember({ user_id: 'user-editor', role: 'editor' }),
            mockMember({ user_id: 'user-mentioned', role: 'viewer' }),
          ],
          isAdmin: false,
          canWrite: true,
        },
      })

      await flushPromises()

      const editButton = wrapper.find('.btn-link')
      await editButton.trigger('click')
      await flushPromises()

      const vm = wrapper.vm as unknown as { editMentions: { user_id: string; display_name_snapshot: string }[] }
      vm.editMentions.splice(0, vm.editMentions.length)

      const editTextarea = wrapper.find('.comment-edit-form textarea')
      await editTextarea.setValue('No mentions now')

      const saveButton = wrapper.find('.btn-primary-sm')
      await saveButton.trigger('click')
      await flushPromises()

      expect(updateComment).toHaveBeenCalled()
      const updatePayload = vi.mocked(updateComment).mock.calls[0][3]
      expect(updatePayload).toHaveProperty('mentions')
      expect(updatePayload.mentions).toEqual([])
    })

    it('does not include left/removed mentions in edit state or payload', async () => {
      const activeMention: MentionResponse = {
        user_id: 'user-active',
        display_name_snapshot: '成员 user-act（编辑）',
        status: 'active',
      }
      const leftMention: MentionResponse = {
        user_id: null,
        display_name_snapshot: '成员 user-lef（已离开）',
        status: 'left',
      }
      const removedMention: MentionResponse = {
        user_id: 'user-removed',
        display_name_snapshot: '成员 user-rem（已移除）',
        status: 'removed',
      }
      const commentWithMixedMentions = mockComment({
        author_user_id: 'user-editor',
        mentions: [activeMention, leftMention, removedMention],
      })
      vi.mocked(listComments).mockResolvedValue({ comments: [commentWithMixedMentions] })

      const wrapper = mount(StoryCommentsPanel, {
        props: {
          familyId: 'family-1',
          storyId: 'story-1',
          currentUserId: 'user-editor',
          members: [
            mockMember({ user_id: 'user-editor', role: 'editor' }),
            mockMember({ user_id: 'user-active', role: 'editor' }),
          ],
          isAdmin: false,
          canWrite: true,
        },
      })

      await flushPromises()

      const editButton = wrapper.find('.btn-link')
      await editButton.trigger('click')
      await flushPromises()

      const vm = wrapper.vm as unknown as { editMentions: { user_id: string; display_name_snapshot: string }[] }
      expect(vm.editMentions.length).toBe(1)
      expect(vm.editMentions[0].user_id).toBe('user-active')
      expect(vm.editMentions.some((m) => m.display_name_snapshot.includes('已离开'))).toBe(false)
      expect(vm.editMentions.some((m) => m.display_name_snapshot.includes('已移除'))).toBe(false)
    })
  })
})
