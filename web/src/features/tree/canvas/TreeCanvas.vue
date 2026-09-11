<script setup lang="ts">
import {
  VueFlow,
  useVueFlow,
  type Edge,
  type Node,
  type NodeMouseEvent,
} from '@vue-flow/core'
import { computed, markRaw, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useTreeViewStore } from '../state/treeViewStore'
import PersonNode from './PersonNode.vue'
import UnionNode from './UnionNode.vue'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

const store = useTreeViewStore()
const { layout, selectedPersonId, focusPersonId, zoom } = storeToRefs(store)
const { setViewport, onPaneReady } = useVueFlow({ id: 'family-tree' })

// markRaw 避免 Vue 把组件代理化；as any 对齐 Vue Flow NodeTypesObject
const nodeTypes = {
  person: markRaw(PersonNode),
  union: markRaw(UnionNode),
} as any

const nodes = computed<Node[]>(() => {
  if (!layout.value) return []
  return layout.value.nodes.map((n) => {
    if (n.kind === 'person') {
      return {
        id: n.id,
        type: 'person',
        position: { x: n.x, y: n.y },
        data: {
          ...(n.data ?? {}),
          selected: n.personId === selectedPersonId.value,
          isFocus: n.personId === focusPersonId.value,
        },
        draggable: true,
        selectable: true,
      }
    }
    return {
      id: n.id,
      type: 'union',
      position: { x: n.x, y: n.y },
      data: n.data ?? {},
      draggable: false,
      selectable: false,
    }
  })
})

const edges = computed<Edge[]>(() => {
  if (!layout.value) return []
  return layout.value.edges.map((e) => {
    const ended = e.data?.ended === true
    const isBar = e.kind === 'union-bar'
    return {
      id: e.id,
      source: e.source,
      target: e.target,
      type: 'default',
      animated: false,
      style: {
        stroke: ended ? '#8a8580' : '#2f5d50',
        strokeWidth: isBar ? 2.5 : 1.5,
        strokeDasharray:
          ended || e.data?.subtype === 'adoptive' ? '6 4' : undefined,
      },
      data: e.data,
    }
  })
})

onPaneReady(() => {
  // 默认 zoom 100%，禁止 fitView 把姓名缩到不可读
  setViewport({ x: 80, y: 60, zoom: zoom.value || 1 })
})

watch(zoom, (z) => {
  setViewport({ x: 80, y: 60, zoom: z })
})

function onNodeClick(ev: NodeMouseEvent) {
  const personId = (ev.node.data as { person?: { id: string } })?.person?.id
  if (personId) store.selectPerson(personId)
}

function onNodeDoubleClick(ev: NodeMouseEvent) {
  const personId = (ev.node.data as { person?: { id: string } })?.person?.id
  if (personId) store.setFocus(personId)
}
</script>

<template>
  <div class="tree-canvas">
    <VueFlow
      id="family-tree"
      :nodes="nodes"
      :edges="edges"
      :node-types="nodeTypes"
      :min-zoom="0.4"
      :max-zoom="2"
      :default-viewport="{ x: 80, y: 60, zoom: 1 }"
      :fit-view-on-init="false"
      @node-click="onNodeClick"
      @node-double-click="onNodeDoubleClick"
    />
  </div>
</template>

<style scoped>
.tree-canvas {
  width: 100%;
  height: 100%;
  background-color: var(--color-paper, #faf9f7);
  background-image: radial-gradient(#e8e4dc 1px, transparent 1px);
  background-size: 20px 20px;
}
.tree-canvas :deep(.vue-flow) {
  width: 100%;
  height: 100%;
  background: transparent;
}
</style>
