<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { PersonDTO } from '../api/types'

const props = defineProps<{
  data: {
    person: PersonDTO
    isFocus?: boolean
    selected?: boolean
  }
}>()

function metaLine(p: PersonDTO): string {
  const birth = p.birthYear != null ? String(p.birthYear) : '?'
  const death =
    p.deceased || p.deathYear != null
      ? String(p.deathYear ?? '已故')
      : ''
  return death ? `${birth}–${death}` : `生 ${birth}`
}
</script>

<template>
  <div
    class="person-node"
    :class="{ focus: data.isFocus, selected: data.selected }"
    :title="data.person.displayName"
  >
    <Handle type="target" :position="Position.Top" class="handle" />
    <div class="name">{{ data.person.displayName }}</div>
    <div class="meta">{{ metaLine(data.person) }}</div>
    <Handle type="source" :position="Position.Bottom" class="handle" />
  </div>
</template>

<style scoped>
.person-node {
  width: 200px;
  min-height: 72px;
  box-sizing: border-box;
  padding: 10px 12px;
  background: var(--color-card, #fff);
  border: 1.5px solid var(--color-border, #d8d4cc);
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  font-family: var(--font-cn, sans-serif);
  color: var(--color-ink, #1f1f1f);
  cursor: pointer;
  user-select: none;
}
.person-node.focus {
  border-width: 2.5px;
  border-color: var(--color-accent, #2f5d50);
  box-shadow: 0 0 0 3px rgba(47, 93, 80, 0.18);
}
.person-node.selected {
  border-color: var(--color-accent, #2f5d50);
}
.name {
  /* 长辈字号：树姓名 18px semibold */
  font-size: var(--type-tree-name, 18px);
  font-weight: var(--type-tree-name-weight, 600);
  line-height: var(--type-tree-name-lh, 1.35);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.meta {
  margin-top: 4px;
  font-size: var(--type-tree-meta, 13px);
  color: var(--color-ink-muted, #2a2a2a);
  opacity: 0.75;
}
.handle {
  width: 6px;
  height: 6px;
  background: transparent;
  border: none;
}
</style>
