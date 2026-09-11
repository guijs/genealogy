import { defineStore } from 'pinia'
import { computed, ref, shallowRef } from 'vue'
import {
  addRelationship,
  type AddRelationshipParams,
  DEFAULT_GRAPH_DEPTH,
  DEMO_GRAPH_PARAMS,
  fetchFamilyGraph,
  isUsingGraphApi,
} from '../api/graphClient'
import {
  createPerson as apiCreatePerson,
  updatePerson as apiUpdatePerson,
  listPersons as apiListPersons,
  PersonApiError,
  type CreatePersonRequest,
  type UpdatePersonRequest,
} from '../api/personClient'
import {
  createUnion as apiCreateUnion,
  endUnion as apiEndUnion,
  UnionApiError,
} from '../api/unionClient'
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
  /** 当前加载的 familyId（用于 person 写入） */
  const currentFamilyId = ref<string>(DEMO_GRAPH_PARAMS.familyId)
  /** 操作中状态（添加/编辑成员） */
  const submitting = ref(false)
  /** API 错误消息（短暂显示后清除） */
  const errorMessage = shallowRef<string | null>(null)
  /** 成功消息 */
  const successMessage = shallowRef<string | null>(null)
  /** 编辑模式开关 */
  const editMode = ref(false)
  /** 添加成员表单显示 */
  const addFormOpen = ref(false)
  /** 添加配偶表单显示 */
  const addSpouseFormOpen = ref(false)
  /** 结束婚姻表单显示 */
  const endMarriageFormOpen = ref(false)
  /** 当前要结束的婚姻 ID */
  const endMarriageId = ref<string | null>(null)
  /** 空家族状态（新家族无成员时） */
  const emptyFamily = ref(false)

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
    emptyFamily.value = false
    const targetFamilyId = familyId ?? DEMO_GRAPH_PARAMS.familyId
    try {
      graph.value = await fetchFamilyGraph({
        familyId: targetFamilyId,
        rootPersonId: DEMO_GRAPH_PARAMS.rootPersonId,
        depth: DEMO_GRAPH_PARAMS.depth,
      })
      currentFamilyId.value = targetFamilyId
      focusPersonId.value = graph.value.rootPersonId
      zoom.value = 1
    } finally {
      loading.value = false
    }
  }

  /**
   * 加载指定家族的图。真 API 模式下根据 familyId 加载家族树。
   * @param familyId 家族 ID
   * @param rootPersonId 可选的根人物 ID；若不传则自动选取家族第一个成员
   */
  async function loadFamily(familyId: string, rootPersonId?: string) {
    loading.value = true
    usingGraphApi.value = isUsingGraphApi()
    emptyFamily.value = false
    currentFamilyId.value = familyId

    try {
      let rootId = rootPersonId

      // 若无 rootPersonId，获取家族成员列表并取第一个
      if (!rootId && isUsingGraphApi()) {
        try {
          const { persons } = await apiListPersons(familyId)
          if (persons.length === 0) {
            // 新家族无成员，显示空状态
            emptyFamily.value = true
            graph.value = null
            return
          }
          rootId = persons[0].id
        } catch (e) {
          // 列表失败时回退到演示模式
          console.warn('Failed to list persons, falling back to demo:', e)
          await loadDemo(familyId)
          return
        }
      }

      // 若仍无 rootId（mock 模式且未传 rootPersonId），使用演示默认值
      if (!rootId) {
        rootId = DEMO_GRAPH_PARAMS.rootPersonId
      }

      graph.value = await fetchFamilyGraph({
        familyId,
        rootPersonId: rootId,
        depth: DEFAULT_GRAPH_DEPTH,
      })
      focusPersonId.value = graph.value.rootPersonId
      zoom.value = 1
    } finally {
      loading.value = false
    }
  }


  function clearMessages() {
    errorMessage.value = null
    successMessage.value = null
  }

  function showError(msg: string) {
    errorMessage.value = msg
    setTimeout(() => {
      if (errorMessage.value === msg) errorMessage.value = null
    }, 4000)
  }

  function showSuccess(msg: string) {
    successMessage.value = msg
    setTimeout(() => {
      if (successMessage.value === msg) successMessage.value = null
    }, 2500)
  }

  /**
   * 创建新成员（Phase2-A）
   * 成功后自动 reloadGraph
   */
  async function createPerson(data: CreatePersonRequest): Promise<boolean> {
    if (!usingGraphApi.value) {
      showError('添加成员需要连接真实 API（当前为 mock 模式）')
      return false
    }
    clearMessages()
    submitting.value = true
    try {
      await apiCreatePerson(currentFamilyId.value, data)
      showSuccess('成员添加成功')
      addFormOpen.value = false
      await reloadGraph()
      return true
    } catch (e) {
      const msg = e instanceof PersonApiError ? e.message : '添加成员失败'
      showError(msg)
      return false
    } finally {
      submitting.value = false
    }
  }

  /**
   * 更新成员信息（Phase2-A）
   * 成功后自动 reloadGraph
   */
  async function updatePerson(
    personId: string,
    data: UpdatePersonRequest,
  ): Promise<boolean> {
    if (!usingGraphApi.value) {
      showError('编辑成员需要连接真实 API（当前为 mock 模式）')
      return false
    }
    clearMessages()
    submitting.value = true
    try {
      await apiUpdatePerson(currentFamilyId.value, personId, data)
      showSuccess('保存成功')
      editMode.value = false
      await reloadGraph()
      return true
    } catch (e) {
      const msg = e instanceof PersonApiError ? e.message : '保存成员失败'
      showError(msg)
      return false
    } finally {
      submitting.value = false
    }
  }

  /**
   * 创建婚姻关系（Phase2-B）
   * 成功后自动 reloadGraph
   * @param partnerBId 配偶的 personId（当前选中人物为 partnerA）
   * @param startedAt 可选的婚姻开始日期
   */
  async function addSpouse(
    partnerBId: string,
    startedAt?: string | null,
  ): Promise<boolean> {
    if (!usingGraphApi.value) {
      showError('添加配偶需要连接真实 API（当前为 mock 模式）')
      return false
    }
    if (!selectedPersonId.value) {
      showError('请先选择一个人物')
      return false
    }
    clearMessages()
    submitting.value = true
    try {
      await apiCreateUnion(
        currentFamilyId.value,
        selectedPersonId.value,
        partnerBId,
        startedAt,
      )
      showSuccess('配偶添加成功')
      addSpouseFormOpen.value = false
      await reloadGraph()
      return true
    } catch (e) {
      const msg = e instanceof UnionApiError ? e.message : '添加配偶失败'
      showError(msg)
      return false
    } finally {
      submitting.value = false
    }
  }

  /**
   * 结束婚姻关系（Phase2-B）
   * 成功后自动 reloadGraph
   * @param marriageId 婚姻 ID
   * @param endedReason 可选的结束原因
   * @param endedAt 可选的结束日期
   */
  async function endMarriage(
    marriageId: string,
    endedReason?: string | null,
    endedAt?: string | null,
  ): Promise<boolean> {
    if (!usingGraphApi.value) {
      showError('结束婚姻需要连接真实 API（当前为 mock 模式）')
      return false
    }
    clearMessages()
    submitting.value = true
    try {
      await apiEndUnion(currentFamilyId.value, marriageId, endedReason, endedAt)
      showSuccess('婚姻状态已更新')
      endMarriageFormOpen.value = false
      endMarriageId.value = null
      await reloadGraph()
      return true
    } catch (e) {
      const msg = e instanceof UnionApiError ? e.message : '结束婚姻失败'
      showError(msg)
      return false
    } finally {
      submitting.value = false
    }
  }

  function openAddSpouseForm() {
    addSpouseFormOpen.value = true
  }

  function closeAddSpouseForm() {
    addSpouseFormOpen.value = false
  }

  function openEndMarriageForm(marriageId: string) {
    endMarriageId.value = marriageId
    endMarriageFormOpen.value = true
  }

  function closeEndMarriageForm() {
    endMarriageFormOpen.value = false
    endMarriageId.value = null
  }

  function openAddForm() {
    addFormOpen.value = true
  }

  function closeAddForm() {
    addFormOpen.value = false
  }

  function startEdit() {
    editMode.value = true
  }

  function cancelEdit() {
    editMode.value = false
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
    currentFamilyId,
    submitting,
    errorMessage,
    successMessage,
    editMode,
    addFormOpen,
    addSpouseFormOpen,
    endMarriageFormOpen,
    endMarriageId,
    emptyFamily,
    layout,
    selectedPerson,
    selectedKin,
    truncated,
    truncateReason,
    loadDemo,
    loadFamily,
    reloadGraph,
    selectPerson,
    closeDrawer,
    setFocus,
    addParentChild,
    createPerson,
    updatePerson,
    addSpouse,
    endMarriage,
    openAddForm,
    closeAddForm,
    openAddSpouseForm,
    closeAddSpouseForm,
    openEndMarriageForm,
    closeEndMarriageForm,
    startEdit,
    cancelEdit,
    clearMessages,
  }
})
