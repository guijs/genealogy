import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  chenDivorceRemarriageFixture,
  fetchGraphFixture,
} from '../api/fixture'
import type { GraphProjection, PersonDTO } from '../api/types'
import { deriveKinForPerson, layoutUnionGraph } from '../layout/unionLayout'

export const useTreeViewStore = defineStore('treeView', () => {
  const graph = ref<GraphProjection | null>(null)
  const focusPersonId = ref<string>(chenDivorceRemarriageFixture.rootPersonId)
  const selectedPersonId = ref<string | null>(null)
  const drawerOpen = ref(false)
  const loading = ref(false)
  /** 默认画布缩放 100%（长辈字号验收） */
  const zoom = ref(1)

  const layout = computed(() => {
    if (!graph.value) return null
    return layoutUnionGraph(graph.value, {
      focusPersonId: focusPersonId.value,
      up: 1,
      down: 2,
    })
  })

  const selectedPerson = computed<PersonDTO | null>(() => {
    if (!graph.value || !selectedPersonId.value) return null
    return (
      graph.value.persons.find((p) => p.id === selectedPersonId.value) ?? null
    )
  })

  const selectedKin = computed(() => {
    if (!graph.value || !selectedPersonId.value) {
      return { parents: [], spouses: [], children: [] }
    }
    return deriveKinForPerson(selectedPersonId.value, graph.value)
  })

  const truncated = computed(() => graph.value?.truncated === true)
  const truncateReason = computed(
    () => graph.value?.truncateReason ?? '已达展开上限',
  )

  async function loadDemo(familyId?: string) {
    loading.value = true
    try {
      graph.value = await fetchGraphFixture(familyId)
      focusPersonId.value = graph.value.rootPersonId
      zoom.value = 1
    } finally {
      loading.value = false
    }
  }

  function selectPerson(personId: string) {
    selectedPersonId.value = personId
    drawerOpen.value = true
  }

  function closeDrawer() {
    drawerOpen.value = false
  }

  function setFocus(personId: string) {
    focusPersonId.value = personId
  }

  return {
    graph,
    focusPersonId,
    selectedPersonId,
    drawerOpen,
    loading,
    zoom,
    layout,
    selectedPerson,
    selectedKin,
    truncated,
    truncateReason,
    loadDemo,
    selectPerson,
    closeDrawer,
    setFocus,
  }
})
