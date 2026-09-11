<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { ref, watch } from 'vue'
import { useTreeViewStore } from '../state/treeViewStore'

const store = useTreeViewStore()
const {
  drawerOpen,
  selectedPerson,
  selectedKin,
  usingGraphApi,
  editMode,
  submitting,
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
</script>

<template>
  <aside v-if="drawerOpen && selectedPerson" class="drawer" aria-label="人物详情">
    <header class="drawer-head">
      <h2 class="detail-name">{{ selectedPerson.displayName }}</h2>
      <div class="head-actions">
        <button
          v-if="!editMode && usingGraphApi"
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
      <h3 class="section-title">配偶</h3>
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
</style>
