<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import TreeCanvas from '../features/tree/canvas/TreeCanvas.vue'
import PersonDetailDrawer from '../features/tree/panels/PersonDetailDrawer.vue'
import { useTreeViewStore } from '../features/tree/state/treeViewStore'

const store = useTreeViewStore()
const { loading, truncated, truncateReason, graph, usingGraphApi } =
  storeToRefs(store)

onMounted(() => {
  void store.loadDemo()
})
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div>
        <h1>家族网络（只读投影）</h1>
        <p class="sub">
          <template v-if="usingGraphApi">
            已接真 API ·
            <code>GET /api/v1/families/{'{familyId}'}/graph</code>
          </template>
          <template v-else>
            当前使用 fixture mock · 对接未来
            <code>GET /api/v1/families/{'{familyId}'}/graph</code>
          </template>
        </p>
      </div>
      <div v-if="graph" class="meta">
        焦点：{{ graph.rootPersonId }} · depth={{ graph.depth }}
        <span class="hint">（up1+down2+焦点 ⇒ depth=3）</span>
      </div>
    </header>

    <div v-if="truncated" class="banner" role="status">
      {{ truncateReason || '已达展开上限' }}
      <span class="banner-hint">可换焦点或收起旁支（P0 示意）</span>
    </div>

    <div class="stage">
      <p v-if="loading" class="loading">加载投影…</p>
      <TreeCanvas v-else />
      <PersonDetailDrawer />
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  font-family: var(--font-cn, sans-serif);
  background: var(--color-paper, #faf9f7);
  color: var(--color-ink, #1f1f1f);
}
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-border, #d8d4cc);
  background: #fff;
}
.topbar h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #666;
}
.sub code {
  font-size: 12px;
  background: #f3f1ec;
  padding: 1px 4px;
  border-radius: 4px;
}
.meta {
  font-size: 13px;
  color: #555;
}
.hint {
  color: #888;
}
.banner {
  padding: 10px 20px;
  background: var(--color-banner, #fff4e5);
  border-bottom: 1px solid var(--color-banner-border, #e6a23c);
  font-size: 15px;
  font-weight: 500;
}
.banner-hint {
  margin-left: 12px;
  font-weight: 400;
  font-size: 13px;
  color: #8a6d3b;
}
.stage {
  position: relative;
  flex: 1;
  min-height: 0;
}
.loading {
  padding: 24px;
}
</style>
