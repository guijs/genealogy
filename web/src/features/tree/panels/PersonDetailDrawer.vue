<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { ref, watch } from 'vue'
import { SIBLING_KIND_LABEL } from '../layout/unionLayout'
import { useTreeViewStore } from '../state/treeViewStore'

const store = useTreeViewStore()
const {
  drawerOpen,
  selectedPerson,
  selectedKin,
  selectedSiblings,
  usingGraphApi,
  editMode,
  submitting,
  selectedPersonHidden,
} = storeToRefs(store)

const editFirstName = ref('')
const editLastName = ref('')

watch(
  () => [selectedPerson.value, editMode.value] as const,
  ([person, editing]) => {
    if (person && editing) {
      const parts = person.displayName.split(/\s+/)
      editLastName.value = parts[0] ?? ''
      editFirstName.value = parts.slice(1).join(' ') || ''
    }
  },
  { immediate: true },
)

async function handleSave() {
  if (!selectedPerson.value) return
  const firstName = editFirstName.value.trim()
  const lastName = editLastName.value.trim()
  if (!firstName || !lastName) return

  await store.updatePerson(selectedPerson.value.id, {
    first_name: firstName,
    last_name: lastName,
  })
}

function handleCancel() {
  store.cancelEdit()
}

const statusLabel: Record<string, string> = {
  active: '存续',
  divorced: '离婚',
  widowed: '丧偶',
  ended: '已结束',
}

const roleLabel: Record<string, string> = {
  father: '父',
  mother: '母',
  parent: '亲',
}

async function handleHide() {
  if (!selectedPerson.value) return
  await store.hidePerson(selectedPerson.value.id)
}

async function handleRestore() {
  if (!selectedPerson.value) return
  await store.restorePerson(selectedPerson.value.id)
}
</script>

<template>
  <aside v-if="drawerOpen && selectedPerson" class="drawer" aria-label="人物详情">
    <header class="drawer-head">
      <div class="head-title">
        <h2 class="detail-name">{{ selectedPerson.displayName }}</h2>
        <span v-if="selectedPersonHidden" class="hidden-badge">已隐藏</span>
      </div>
      <div class="head-actions">
        <button
          v-if="!editMode && usingGraphApi && !selectedPersonHidden"
          type="button"
          class="btn-edit"
          @click="store.startEdit()"
        >
          编辑
        </button>
        <button type="button" class="close" @click="store.closeDrawer()">关闭</button>
      </div>
    </header>

    <!-- 编辑模式 -->
    <div v-if="editMode" class="edit-form">
      <div class="form-row">
        <label class="form-label">姓</label>
        <input
          v-model="editLastName"
          type="text"
          class="form-input"
          placeholder="姓氏"
          :disabled="submitting"
        />
      </div>
      <div class="form-row">
        <label class="form-label">名</label>
        <input
          v-model="editFirstName"
          type="text"
          class="form-input"
          placeholder="名字"
          :disabled="submitting"
        />
      </div>
      <div class="form-actions">
        <button
          type="button"
          class="btn-save"
          :disabled="submitting || !editFirstName.trim() || !editLastName.trim()"
          @click="handleSave"
        >
          {{ submitting ? '保存中…' : '保存' }}
        </button>
        <button
          type="button"
          class="btn-cancel"
          :disabled="submitting"
          @click="handleCancel"
        >
          取消
        </button>
      </div>
      <p v-if="!usingGraphApi" class="mock-hint">
        mock 模式下无法保存，需连接真实 API
      </p>
    </div>

    <!-- 只读模式 -->
    <template v-else>
      <p v-if="selectedPerson.birthYear || selectedPerson.deathYear" class="caption">
        <template v-if="selectedPerson.birthYear">生 {{ selectedPerson.birthYear }}</template>
        <template v-if="selectedPerson.deathYear"> · 卒 {{ selectedPerson.deathYear }}</template>
      </p>

      <p v-if="!usingGraphApi" class="mock-hint topmost">
        mock 模式：编辑功能需连接真实 API
      </p>
    </template>

    <section class="section">
      <h3 class="section-title">父母</h3>
      <ul v-if="selectedKin.parents.length" class="kin-list">
        <li v-for="row in selectedKin.parents" :key="row.person.id">
          <span class="kin-label">{{ roleLabel[row.role ?? ''] ?? '亲' }}</span>
          <button
            type="button"
            class="kin-name link"
            @click="store.selectPerson(row.person.id)"
          >
            {{ row.person.displayName }}
          </button>
          <span v-if="row.subtype === 'adoptive'" class="tag">养</span>
        </li>
      </ul>
      <p v-else class="empty">暂无（缺边）</p>
    </section>

    <section class="section">
      <div class="section-header">
        <h3 class="section-title">配偶</h3>
        <button
          v-if="usingGraphApi && !editMode"
          type="button"
          class="btn-action btn-add-spouse"
          :disabled="submitting"
          @click="store.openAddSpouseForm()"
        >
          添加配偶
        </button>
      </div>
      <ul v-if="selectedKin.spouses.length" class="kin-list">
        <li v-for="row in selectedKin.spouses" :key="row.marriageId">
          <span class="kin-label">配偶</span>
          <button
            type="button"
            class="kin-name link"
            @click="store.selectPerson(row.person.id)"
          >
            {{ row.person.displayName }}
          </button>
          <span class="tag" :class="{ ended: row.status !== 'active' }">
            {{ statusLabel[row.status] ?? row.status }}
          </span>
          <button
            v-if="row.status === 'active' && usingGraphApi && !editMode"
            type="button"
            class="btn-end-marriage"
            :disabled="submitting"
            @click="store.openEndMarriageForm(row.marriageId)"
          >
            结束
          </button>
        </li>
      </ul>
      <p v-else class="empty">暂无</p>
    </section>

    <section class="section">
      <h3 class="section-title">子女</h3>
      <ul v-if="selectedKin.children.length" class="kin-list">
        <li v-for="row in selectedKin.children" :key="row.person.id">
          <span class="kin-label">子女</span>
          <button
            type="button"
            class="kin-name link"
            @click="store.selectPerson(row.person.id)"
          >
            {{ row.person.displayName }}
          </button>
          <span v-if="row.subtype === 'adoptive'" class="tag">养</span>
        </li>
      </ul>
      <p v-else class="empty">暂无</p>
    </section>

    <section v-if="selectedSiblings.length > 0" class="section">
      <h3 class="section-title">兄弟姐妹</h3>
      <ul class="kin-list">
        <li v-for="sib in selectedSiblings" :key="sib.siblingId">
          <span class="kin-label">{{ SIBLING_KIND_LABEL[sib.kind] }}</span>
          <button
            v-if="sib.person"
            type="button"
            class="kin-name link"
            @click="store.selectPerson(sib.siblingId)"
          >
            {{ sib.person.displayName }}
          </button>
          <span v-else class="kin-name fallback">{{ sib.siblingId }}</span>
        </li>
      </ul>
    </section>

    <!-- 隐藏/恢复操作 -->
    <section v-if="usingGraphApi && !editMode" class="section section-actions">
      <div v-if="selectedPersonHidden" class="hidden-notice">
        <p class="hidden-text">此成员已从家族树隐藏，不会显示在图谱中。</p>
        <button
          type="button"
          class="btn-restore"
          :disabled="submitting"
          @click="handleRestore"
        >
          {{ submitting ? '恢复中…' : '恢复显示' }}
        </button>
      </div>
      <div v-else class="hide-action">
        <button
          type="button"
          class="btn-hide"
          :disabled="submitting"
          @click="handleHide"
        >
          {{ submitting ? '处理中…' : '隐藏成员' }}
        </button>
        <p class="hide-hint">隐藏后成员将不显示在家族树中，可随时恢复</p>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.drawer {
  position: absolute;
  top: 0;
  right: 0;
  width: min(360px, 100%);
  height: 100%;
  background: var(--color-card, #fff);
  border-left: 1px solid var(--color-border, #d8d4cc);
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.06);
  padding: 20px 20px 32px;
  overflow: auto;
  font-family: var(--font-cn, sans-serif);
  color: var(--color-ink, #1f1f1f);
  z-index: 20;
  box-sizing: border-box;
}
.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.detail-name {
  margin: 0;
  /* 详情主姓名 22px */
  font-size: var(--type-detail-name, 22px);
  font-weight: var(--type-detail-name-weight, 600);
  line-height: 1.35;
}
.close,
.btn-edit {
  border: 1px solid var(--color-border, #d8d4cc);
  background: #fff;
  border-radius: 6px;
  padding: 4px 10px;
  cursor: pointer;
  font-size: 13px;
}
.btn-edit {
  color: var(--color-accent, #2f5d50);
}
.btn-edit:hover {
  background: #f8f7f5;
}
.edit-form {
  margin-top: 16px;
  padding: 16px;
  background: #faf9f7;
  border-radius: 8px;
}
.form-row {
  margin-bottom: 12px;
}
.form-label {
  display: block;
  font-size: var(--type-kin-label, 15px);
  font-weight: 500;
  color: var(--color-ink-muted, #2a2a2a);
  margin-bottom: 4px;
}
.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  font-size: var(--type-body, 15px);
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
.form-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
.btn-save {
  flex: 1;
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: var(--type-body, 15px);
  font-weight: 500;
  cursor: pointer;
}
.btn-save:hover:not(:disabled) {
  opacity: 0.9;
}
.btn-save:disabled {
  background: #ccc;
  cursor: not-allowed;
}
.btn-cancel {
  padding: 8px 16px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: #fff;
  font-size: var(--type-body, 15px);
  cursor: pointer;
}
.btn-cancel:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.mock-hint {
  margin: 12px 0 0;
  padding: 8px 12px;
  background: #fff4e5;
  border-radius: 6px;
  font-size: 13px;
  color: #8a6d3b;
}
.mock-hint.topmost {
  margin-top: 16px;
}
.caption {
  margin: 8px 0 0;
  font-size: var(--type-caption, 13px);
  color: var(--color-ink-muted, #2a2a2a);
  opacity: 0.8;
}
.section {
  margin-top: 20px;
}
.section-title {
  margin: 0 0 8px;
  font-size: var(--type-detail-section, 16px);
  font-weight: 600;
}
.kin-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.kin-list li {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 6px 0;
  min-height: 28px;
}
.kin-label {
  font-size: var(--type-kin-label, 15px);
  font-weight: 500;
  color: var(--color-ink-muted, #2a2a2a);
  min-width: 2.5em;
}
.kin-name {
  font-size: var(--type-kin-name, 16px);
  font-weight: 500;
}
.link {
  background: none;
  border: none;
  padding: 0;
  color: var(--color-accent, #2f5d50);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.tag {
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #e8f0ed;
  color: var(--color-accent, #2f5d50);
}
.tag.ended {
  background: #f0eeeb;
  color: var(--color-ended, #8a8580);
}
.empty {
  margin: 0;
  font-size: var(--type-body, 15px);
  color: #888;
}
.fallback {
  color: var(--color-ink-muted, #888);
  font-style: italic;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.section-header .section-title {
  margin: 0;
}
.btn-action {
  padding: 4px 10px;
  border: 1px solid var(--color-accent, #2f5d50);
  border-radius: 4px;
  background: transparent;
  color: var(--color-accent, #2f5d50);
  font-size: 12px;
  cursor: pointer;
}
.btn-action:hover:not(:disabled) {
  background: rgba(47, 93, 80, 0.08);
}
.btn-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-end-marriage {
  margin-left: auto;
  padding: 2px 8px;
  border: 1px solid var(--color-ended, #8a8580);
  border-radius: 4px;
  background: transparent;
  color: var(--color-ended, #8a8580);
  font-size: 11px;
  cursor: pointer;
}
.btn-end-marriage:hover:not(:disabled) {
  background: rgba(138, 133, 128, 0.1);
}
.btn-end-marriage:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.head-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hidden-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  background: #fff4e5;
  color: #8a6d3b;
  font-size: 12px;
  font-weight: 500;
}
.section-actions {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border, #d8d4cc);
}
.hidden-notice {
  padding: 16px;
  background: #fff4e5;
  border-radius: 8px;
}
.hidden-text {
  margin: 0 0 12px;
  font-size: 14px;
  color: #8a6d3b;
}
.btn-restore {
  width: 100%;
  padding: 10px 16px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}
.btn-restore:hover:not(:disabled) {
  opacity: 0.9;
}
.btn-restore:disabled {
  background: #ccc;
  cursor: not-allowed;
}
.hide-action {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.btn-hide {
  width: 100%;
  padding: 10px 16px;
  border: 1px solid var(--color-ended, #8a8580);
  border-radius: 6px;
  background: transparent;
  color: var(--color-ended, #8a8580);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}
.btn-hide:hover:not(:disabled) {
  background: rgba(138, 133, 128, 0.1);
}
.btn-hide:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.hide-hint {
  margin: 0;
  font-size: 12px;
  color: #888;
  text-align: center;
}
</style>
