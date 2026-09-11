<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{
  data: {
    ended?: boolean
    marriageStatus?: string
  }
}>()
</script>

<template>
  <div class="union-node" :class="{ ended: data.ended }" title="婚姻锚点">
    <Handle type="target" :position="Position.Top" class="handle" />
    <div class="bar" :class="{ dashed: data.ended }" />
    <Handle type="source" :position="Position.Bottom" class="handle" />
  </div>
</template>

<style scoped>
.union-node {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}
.bar {
  width: 20px;
  height: 0;
  border-top: 3px solid var(--color-accent, #2f5d50);
}
.bar.dashed {
  border-top-style: dashed;
  border-top-color: var(--color-ended, #8a8580);
}
.handle {
  width: 4px;
  height: 4px;
  background: transparent;
  border: none;
}
</style>
