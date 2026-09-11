import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  addRelationship,
  type AddRelationshipParams,
  DEMO_GRAPH_PARAMS,
  fetchFamilyGraph,
  isUsingGraphApi,
} from '../api/graphClient'
import type { GraphProjection, PersonDTO } from '../api/types'
import { deriveKinForPerson, layoutUnionGraph } from '../layout/unionLayout'

export const useTreeViewStore = defineStore('treeView', () => {
  const graph = ref<GraphProjection | null>(null)
  // 演示 root 保留自 fixture / DEMO_GRAPH_PARAMS
  const focusPersonId = ref<string>(DEMO_GRAPH_PARAMS.rootPersonId)
  const selectedPersonId = ref<string | null>(null)
  const drawerOpen = ref(false)
  const loading = ref(false)
  /** 默认画布缩放 100%（长辈字号验收） */
  const zoom = ref(1)
  /** 当前数据源：fixture mock vs 真 API（供 UI 文案） */
  const usingGraphApi = ref(isUsingGraphApi())

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

  /**
   * 加载演示家族图。走 fetchFamilyGraph（默认 fixture；env 可切真 API）。
   * 保留演示 familyId / rootPersonId。
   */
  async function loadDemo(familyId?: string) {
    loading.value = true
    usingGraphApi.value = isUsingGraphApi()
    try {
      graph.value = await fetchFamilyGraph({
        familyId: familyId ?? DEMO_GRAPH_PARAMS.familyId,
        rootPersonId: DEMO_GRAPH_PARAMS.rootPersonId,
        depth: DEMO_GRAPH_PARAMS.depth,
      })
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

  /**
   * 重新加载当前家族图投影。
   * 在写操作（如 addParentChild）后调用，使新边可见。
   */
  async function reloadGraph() {
    if (!graph.value) return
    loading.value = true
    usingGraphApi.value = isUsingGraphApi()
    try {
      graph.value = await fetchFamilyGraph({
        familyId: graph.value.familyId,
        rootPersonId: focusPersonId.value,
        depth: graph.value.depth,
      })
    } finally {
      loading.value = false
    }
  }

  /**
   * 添加亲子关系并刷新投影。
   * 仅在使用真 API 时可用。
   */
  async function addParentChild(
    params: Omit<AddRelationshipParams, 'familyId'>,
  ) {
    if (!graph.value) {
      throw new Error('addParentChild: no graph loaded')
    }
    await addRelationship({
      familyId: graph.value.familyId,
      ...params,
    })
    await reloadGraph()
  }

  return {
    graph,
    focusPersonId,
    selectedPersonId,
    drawerOpen,
    loading,
    zoom,
    usingGraphApi,
    layout,
    selectedPerson,
    selectedKin,
    truncated,
    truncateReason,
    loadDemo,
    selectPerson,
    closeDrawer,
    setFocus,
    reloadGraph,
    addParentChild,
  }
})
