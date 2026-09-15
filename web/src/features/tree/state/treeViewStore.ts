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
  hidePerson as apiHidePerson,
  restorePerson as apiRestorePerson,
  PersonApiError,
  HidePersonConflictError,
  type CreatePersonRequest,
  type UpdatePersonRequest,
} from '../api/personClient'
import {
  createUnion as apiCreateUnion,
  endUnion as apiEndUnion,
  UnionApiError,
} from '../api/unionClient'
import {
  dissolveRelationship as apiDissolveRelationship,
  restoreRelationship as apiRestoreRelationship,
  RelationshipApiError,
  AlreadyDissolvedException,
  NotDissolvedException,
  RestoreBlockedException,
} from '../api/relationshipClient'
import type { DissolvedRelationship } from '../api/types'
import type { GraphProjection, PersonDTO } from '../api/types'
import {
  deriveKinForPerson,
  deriveSiblingsForPerson,
  layoutUnionGraph,
} from '../layout/unionLayout'

const DISSOLVED_STORAGE_KEY = 'genealogy:dissolvedRelationships'

function initDissolvedFromStorage(): Record<string, DissolvedRelationship[]> {
  try {
    const raw = localStorage.getItem(DISSOLVED_STORAGE_KEY)
    if (raw) return JSON.parse(raw)
  } catch {
    // ignore parse errors
  }
  return {}
}

function saveDissolvedToStorage(map: Record<string, DissolvedRelationship[]>): void {
  try {
    localStorage.setItem(DISSOLVED_STORAGE_KEY, JSON.stringify(map))
  } catch {
    // ignore storage errors
  }
}

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
  /** 隐藏成员确认弹窗显示（用于处理 409 活跃婚姻冲突） */
  const hideConfirmOpen = ref(false)
  /** 待确认隐藏的成员 ID */
  const hideConfirmPersonId = ref<string | null>(null)
  /** 隐藏确认弹窗的消息 */
  const hideConfirmMessage = ref<string>('')
  /** 当前选中的人物是否已隐藏（用于 drawer 显示 restore 按钮） */
  const selectedPersonHidden = ref(false)
  /** 已隐藏人物的缓存数据（当人物从 graph 消失后仍需在 drawer 显示） */
  const hiddenPersonCache = ref<PersonDTO | null>(null)
  /** 持久化的已隐藏人物列表，按 familyId 存储 {id, displayName} */
  const hiddenPersonsMap = ref<Record<string, Array<{ id: string; displayName: string }>>>({})
  /** 持久化的已解除亲子关系列表，按 familyId 存储（localStorage 同步） */
  const dissolvedRelationshipsMap = ref<Record<string, DissolvedRelationship[]>>(initDissolvedFromStorage())

  const layout = computed(() => {
    if (!graph.value) return null
    return layoutUnionGraph(graph.value, {
      focusPersonId: focusPersonId.value,
      up: 1,
      down: 2,
    })
  })

  const selectedPerson = computed<PersonDTO | null>(() => {
    if (!selectedPersonId.value) return null
    if (selectedPersonHidden.value && hiddenPersonCache.value) {
      return hiddenPersonCache.value
    }
    if (!graph.value) return null
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

  const selectedSiblings = computed(() => {
    if (!graph.value || !selectedPersonId.value) {
      return []
    }
    return deriveSiblingsForPerson(selectedPersonId.value, graph.value)
  })

  const truncated = computed(() => graph.value?.truncated === true)
  const truncateReason = computed(
    () => graph.value?.truncateReason ?? '已达展开上限',
  )

  /** 当前家族的已隐藏人员列表 */
  const hiddenPersonsForCurrentFamily = computed(() => {
    return hiddenPersonsMap.value[currentFamilyId.value] ?? []
  })

  /** 当前家族的已解除亲子关系列表 */
  const dissolvedRelationshipsForCurrentFamily = computed(() => {
    return dissolvedRelationshipsMap.value[currentFamilyId.value] ?? []
  })

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
      
      void refreshHiddenPersons()
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

  /**
   * 隐藏成员（Phase2-C）
   * 成功后自动 reloadGraph；若有活跃婚姻需用户确认
   * @param personId 要隐藏的成员 ID
   * @param confirmActiveUnion 是否确认隐藏有活跃婚姻的成员
   */
  async function hidePerson(
    personId: string,
    confirmActiveUnion = false,
  ): Promise<boolean> {
    if (!usingGraphApi.value) {
      showError('隐藏成员需要连接真实 API（当前为 mock 模式）')
      return false
    }
    clearMessages()
    submitting.value = true

    const personToCache = graph.value?.persons.find((p) => p.id === personId)

    try {
      await apiHidePerson(currentFamilyId.value, personId, confirmActiveUnion)
      showSuccess('成员已隐藏，可点击「恢复」重新显示')
      hideConfirmOpen.value = false
      hideConfirmPersonId.value = null
      hideConfirmMessage.value = ''

      if (personToCache) {
        hiddenPersonCache.value = personToCache
        selectedPersonHidden.value = true
        
        const familyId = currentFamilyId.value
        if (!hiddenPersonsMap.value[familyId]) {
          hiddenPersonsMap.value[familyId] = []
        }
        const existing = hiddenPersonsMap.value[familyId].find(p => p.id === personId)
        if (!existing) {
          hiddenPersonsMap.value[familyId].push({
            id: personToCache.id,
            displayName: personToCache.displayName,
          })
        }
      }

      await reloadGraph()
      return true
    } catch (e) {
      if (e instanceof HidePersonConflictError) {
        hideConfirmPersonId.value = personId
        hideConfirmMessage.value = e.message
        hideConfirmOpen.value = true
        return false
      }
      const msg = e instanceof PersonApiError ? e.message : '隐藏成员失败'
      showError(msg)
      return false
    } finally {
      submitting.value = false
    }
  }

  /**
   * 确认隐藏有活跃婚姻的成员
   */
  async function confirmHidePerson(): Promise<boolean> {
    if (!hideConfirmPersonId.value) return false
    return hidePerson(hideConfirmPersonId.value, true)
  }

  /**
   * 取消隐藏确认
   */
  function cancelHideConfirm() {
    hideConfirmOpen.value = false
    hideConfirmPersonId.value = null
    hideConfirmMessage.value = ''
  }

  /**
   * 恢复已隐藏的成员（Phase2-C）
   * 成功后自动 reloadGraph
   * @param personId 要恢复的成员 ID
   */
  async function restorePerson(personId: string): Promise<boolean> {
    if (!usingGraphApi.value) {
      showError('恢复成员需要连接真实 API（当前为 mock 模式）')
      return false
    }
    clearMessages()
    submitting.value = true
    try {
      await apiRestorePerson(currentFamilyId.value, personId)
      showSuccess('成员已恢复')
      selectedPersonHidden.value = false
      hiddenPersonCache.value = null
      
      const familyId = currentFamilyId.value
      if (hiddenPersonsMap.value[familyId]) {
        hiddenPersonsMap.value[familyId] = hiddenPersonsMap.value[familyId].filter(
          p => p.id !== personId
        )
      }
      
      await reloadGraph()
      return true
    } catch (e) {
      const msg = e instanceof PersonApiError ? e.message : '恢复成员失败'
      showError(msg)
      return false
    } finally {
      submitting.value = false
    }
  }

  /**
   * 从隐藏列表面板恢复成员（不通过 drawer）
   * 成功后刷新隐藏列表和家族图
   * @param personId 要恢复的成员 ID
   */
  async function restorePersonFromPanel(personId: string): Promise<boolean> {
    if (!usingGraphApi.value) {
      showError('恢复成员需要连接真实 API（当前为 mock 模式）')
      return false
    }
    clearMessages()
    submitting.value = true
    try {
      await apiRestorePerson(currentFamilyId.value, personId)
      showSuccess('成员已恢复')
      
      const familyId = currentFamilyId.value
      if (hiddenPersonsMap.value[familyId]) {
        hiddenPersonsMap.value[familyId] = hiddenPersonsMap.value[familyId].filter(
          p => p.id !== personId
        )
      }
      
      await reloadGraph()
      await loadFamily(familyId)
      return true
    } catch (e) {
      const msg = e instanceof PersonApiError ? e.message : '恢复成员失败'
      showError(msg)
      return false
    } finally {
      submitting.value = false
    }
  }

  /**
   * 解除亲子关系（AP-R14）
   * 成功后缓存到 dissolvedRelationshipsMap，刷新 graph
   * @param relationshipId 关系 ID
   * @param parentId 父母 ID
   * @param childId 子女 ID
   * @param parentDisplayName 父母显示名
   * @param childDisplayName 子女显示名
   * @param subtype 关系子类型（biological/adoptive）
   * @param role 角色（father/mother/parent）
   */
  async function dissolveParentChildRelationship(
    relationshipId: string,
    parentId: string,
    childId: string,
    parentDisplayName: string,
    childDisplayName: string,
    subtype?: string,
    role?: string,
  ): Promise<boolean> {
    if (!usingGraphApi.value) {
      showError('解除亲子关系需要连接真实 API（当前为 mock 模式）')
      return false
    }
    clearMessages()
    submitting.value = true
    try {
      await apiDissolveRelationship(currentFamilyId.value, relationshipId)

      const familyId = currentFamilyId.value
      if (!dissolvedRelationshipsMap.value[familyId]) {
        dissolvedRelationshipsMap.value[familyId] = []
      }
      const existing = dissolvedRelationshipsMap.value[familyId].find(
        (r) => r.id === relationshipId,
      )
      if (!existing) {
        dissolvedRelationshipsMap.value[familyId].push({
          id: relationshipId,
          parentId,
          childId,
          parentDisplayName,
          childDisplayName,
          subtype,
          role,
        })
        saveDissolvedToStorage(dissolvedRelationshipsMap.value)
      }

      showSuccess('亲子关系已解除')
      await reloadGraph()
      return true
    } catch (e) {
      if (e instanceof AlreadyDissolvedException) {
        // 409: 服务端已解除，同步 stash 并刷新（防止本地 stash 丢失恢复入口）
        const familyId = currentFamilyId.value
        if (!dissolvedRelationshipsMap.value[familyId]) {
          dissolvedRelationshipsMap.value[familyId] = []
        }
        const existing = dissolvedRelationshipsMap.value[familyId].find(
          (r) => r.id === relationshipId,
        )
        if (!existing) {
          dissolvedRelationshipsMap.value[familyId].push({
            id: relationshipId,
            parentId,
            childId,
            parentDisplayName,
            childDisplayName,
            subtype,
            role,
          })
          saveDissolvedToStorage(dissolvedRelationshipsMap.value)
        }
        showSuccess('关系已解除')
        await reloadGraph()
        return true
      }
      if (e instanceof RelationshipApiError && e.status === 403) {
        showError('没有编辑权限')
        return false
      }
      const msg =
        e instanceof RelationshipApiError ? e.message : '解除亲子关系失败'
      showError(msg)
      return false
    } finally {
      submitting.value = false
    }
  }

  /**
   * 恢复已解除的亲子关系（AP-R14）
   * 成功后从 dissolvedRelationshipsMap 移除，刷新 graph
   * @param relationshipId 关系 ID
   */
  async function restoreParentChildRelationship(
    relationshipId: string,
  ): Promise<boolean> {
    if (!usingGraphApi.value) {
      showError('恢复亲子关系需要连接真实 API（当前为 mock 模式）')
      return false
    }
    clearMessages()
    submitting.value = true
    try {
      await apiRestoreRelationship(currentFamilyId.value, relationshipId)

      const familyId = currentFamilyId.value
      if (dissolvedRelationshipsMap.value[familyId]) {
        dissolvedRelationshipsMap.value[familyId] =
          dissolvedRelationshipsMap.value[familyId].filter(
            (r) => r.id !== relationshipId,
          )
        saveDissolvedToStorage(dissolvedRelationshipsMap.value)
      }

      showSuccess('亲子关系已恢复')
      await reloadGraph()
      return true
    } catch (e) {
      if (e instanceof NotDissolvedException) {
        // 已恢复，同步 stash
        const familyId = currentFamilyId.value
        if (dissolvedRelationshipsMap.value[familyId]) {
          dissolvedRelationshipsMap.value[familyId] =
            dissolvedRelationshipsMap.value[familyId].filter(
              (r) => r.id !== relationshipId,
            )
          saveDissolvedToStorage(dissolvedRelationshipsMap.value)
        }
        showSuccess('关系已恢复')
        await reloadGraph()
        return true
      }
      if (e instanceof RestoreBlockedException) {
        showError(e.message)
        return false
      }
      if (e instanceof RelationshipApiError && e.status === 403) {
        showError('没有编辑权限')
        return false
      }
      const msg =
        e instanceof RelationshipApiError ? e.message : '恢复亲子关系失败'
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
    selectedPersonHidden.value = false
    hiddenPersonCache.value = null
  }

  function closeDrawer() {
    drawerOpen.value = false
    selectedPersonHidden.value = false
    hiddenPersonCache.value = null
  }

  function setFocus(personId: string) {
    focusPersonId.value = personId
  }

  /**
   * 启发式刷新隐藏人员列表
   * 逻辑：listPersons(familyId) \ graph.persons
   * 即：完整人员列表减去当前 graph 中可见的人员 = 隐藏的人员
   */
  async function refreshHiddenPersons(): Promise<void> {
    if (!usingGraphApi.value || !currentFamilyId.value) return
    
    try {
      const { persons: allPersons } = await apiListPersons(currentFamilyId.value)
      const visibleIds = new Set(graph.value?.persons.map(p => p.id) ?? [])
      
      const hiddenPersons = allPersons
        .filter(p => !visibleIds.has(p.id))
        .map(p => ({
          id: p.id,
          displayName: `${p.last_name}${p.first_name}`,
        }))
      
      hiddenPersonsMap.value[currentFamilyId.value] = hiddenPersons
    } catch (e) {
      console.warn('Failed to refresh hidden persons:', e)
    }
  }

  /**
   * 重新加载当前家族图投影。
   * 在写操作（如 addParentChild）后调用，使新边可见。
   */
  async function reloadGraph() {
    if (!graph.value) {
      if (currentFamilyId.value) {
        await loadFamily(currentFamilyId.value)
      }
      return
    }
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
    hideConfirmOpen,
    hideConfirmPersonId,
    hideConfirmMessage,
    selectedPersonHidden,
    hiddenPersonsForCurrentFamily,
    dissolvedRelationshipsForCurrentFamily,
    layout,
    selectedPerson,
    selectedKin,
    selectedSiblings,
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
    hidePerson,
    confirmHidePerson,
    cancelHideConfirm,
    restorePerson,
    restorePersonFromPanel,
    refreshHiddenPersons,
    dissolveParentChildRelationship,
    restoreParentChildRelationship,
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
