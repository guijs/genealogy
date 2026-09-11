<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useTreeViewStore } from '../state/treeViewStore'

const store = useTreeViewStore()
const { drawerOpen, selectedPerson, selectedKin } = storeToRefs(store)

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
      <button type="button" class="close" @click="store.closeDrawer()">关闭</button>
    </header>

    <p v-if="selectedPerson.birthYear || selectedPerson.deathYear" class="caption">
      <template v-if="selectedPerson.birthYear">生 {{ selectedPerson.birthYear }}</template>
      <template v-if="selectedPerson.deathYear"> · 卒 {{ selectedPerson.deathYear }}</template>
    </p>

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
.detail-name {
  margin: 0;
  /* 详情主姓名 22px */
  font-size: var(--type-detail-name, 22px);
  font-weight: var(--type-detail-name-weight, 600);
  line-height: 1.35;
}
.close {
  border: 1px solid var(--color-border, #d8d4cc);
  background: #fff;
  border-radius: 6px;
  padding: 4px 10px;
  cursor: pointer;
  font-size: 13px;
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
