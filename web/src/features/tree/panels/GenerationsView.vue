<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import type { GenerationsProjection } from '../api/types'
import { fetchGenerations, getGenerationLabel, GenerationsApiError } from '../api/generationsClient'
import { useTreeViewStore } from '../state/treeViewStore'

const store = useTreeViewStore()
const { currentFamilyId, focusPersonId, usingGraphApi } = storeToRefs(store)

const loading = ref(false)
const errorMessage = ref<string | null>(null)
const generations = ref<GenerationsProjection | null>(null)

async function loadGenerations() {
  if (!currentFamilyId.value) {
    errorMessage.value = '未选择家族'
    return
  }

  if (!usingGraphApi.value) {
    errorMessage.value = '世代视图需要连接真实 API（当前为 mock 模式）'
    return
  }

  loading.value = true
  errorMessage.value = null

  try {
    generations.value = await fetchGenerations({
      familyId: currentFamilyId.value,
      focusPersonId: focusPersonId.value || undefined,
    })
  } catch (e) {
    if (e instanceof GenerationsApiError) {
      errorMessage.value = e.message
    } else {
      errorMessage.value = '加载世代视图失败'
    }
    generations.value = null
  } finally {
    loading.value = false
  }
}

const sortedGenerations = computed(() => {
  if (!generations.value) return []
  return [...generations.value.generations].sort((a, b) => a.index - b.index)
})

const focusedPersonId = computed(() => generations.value?.focusPersonId)

function handlePersonClick(personId: string) {
  store.selectPerson(personId)
}

onMounted(() => {
  void loadGenerations()
})

watch([currentFamilyId, focusPersonId], () => {
  void loadGenerations()
})

defineExpose({ reload: loadGenerations })
</script>

<template>
  <div class="generations-view">
    <!-- Loading state -->
    <div v-if="loading" class="state-loading">
      <p class="loading-text">加载世代视图…</p>
    </div>

    <!-- Error state -->
    <div v-else-if="errorMessage" class="state-error" role="alert">
      <p class="error-text">{{ errorMessage }}</p>
      <button
        v-if="usingGraphApi"
        type="button"
        class="btn-retry"
        @click="loadGenerations"
      >
        重试
      </button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!generations || sortedGenerations.length === 0" class="state-empty">
      <p class="empty-text">暂无世代数据</p>
    </div>

    <!-- Generations layers -->
    <div v-else class="generations-layers">
      <div
        v-for="gen in sortedGenerations"
        :key="gen.index"
        class="generation-layer"
        :class="{ 'is-ego': gen.index === 0 }"
      >
        <h3 class="layer-label">{{ getGenerationLabel(gen.index) }}</h3>
        <ul class="persons-list">
          <li
            v-for="person in gen.persons"
            :key="person.id"
            class="person-item"
            :class="{
              'is-focus': person.id === focusedPersonId,
              'has-conflict': person.conflict === true,
            }"
          >
            <button
              type="button"
              class="person-button"
              :title="person.displayName + (person.conflict ? '（世代冲突）' : '')"
              @click="handlePersonClick(person.id)"
            >
              <span class="person-name">{{ person.displayName }}</span>
              <span
                v-if="person.conflict"
                class="conflict-badge"
                aria-label="世代冲突"
              >!</span>
            </button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.generations-view {
  height: 100%;
  overflow: auto;
  padding: 20px;
  font-family: var(--font-cn, sans-serif);
  color: var(--color-ink, #1f1f1f);
  background: var(--color-paper, #faf9f7);
}

/* Loading state */
.state-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.loading-text {
  font-size: 16px;
  color: #666;
}

/* Error state */
.state-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 16px;
}
.error-text {
  font-size: 15px;
  color: #c53030;
  text-align: center;
  margin: 0;
}
.btn-retry {
  padding: 8px 20px;
  border: 1px solid var(--color-accent, #2f5d50);
  border-radius: 6px;
  background: transparent;
  color: var(--color-accent, #2f5d50);
  font-size: 14px;
  cursor: pointer;
}
.btn-retry:hover {
  background: rgba(47, 93, 80, 0.08);
}

/* Empty state */
.state-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.empty-text {
  font-size: 15px;
  color: #888;
}

/* Generations layers */
.generations-layers {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.generation-layer {
  padding: 16px;
  background: var(--color-card, #fff);
  border-radius: 10px;
  border: 1px solid var(--color-border, #d8d4cc);
}
.generation-layer.is-ego {
  border-color: var(--color-accent, #2f5d50);
  border-width: 2px;
  background: #f8fbfa;
}

.layer-label {
  margin: 0 0 12px;
  font-size: var(--type-detail-section, 16px);
  font-weight: 600;
  color: var(--color-ink, #1f1f1f);
}
.generation-layer.is-ego .layer-label {
  color: var(--color-accent, #2f5d50);
}

.persons-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.person-item {
  position: relative;
}

.person-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  background: #faf9f7;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s, border-color 0.15s;
}
.person-button:hover {
  background: #f0eeeb;
  border-color: var(--color-accent, #2f5d50);
}
.person-item.is-focus .person-button {
  background: var(--color-accent, #2f5d50);
  border-color: var(--color-accent, #2f5d50);
  color: #fff;
}
.person-item.has-conflict .person-button {
  border-color: #e6a23c;
}

.person-name {
  font-size: var(--type-node-name, 18px);
  font-weight: 500;
  line-height: 1.3;
}

.conflict-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff4e5;
  color: #e6a23c;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.person-item.is-focus .conflict-badge {
  background: rgba(255, 255, 255, 0.9);
}
</style>
