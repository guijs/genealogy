/**
 * Marriage-union 布局器（纯函数，零 Vue 依赖）
 *
 * Person + Marriage + Relationship → 带坐标的 nodes / edges
 * - Person 节点全局唯一
 * - Union 可多个（离婚再婚各一段）
 * - 已结束婚姻在 partner / union-bar edge.data.ended = true
 * - 代数 = 相对焦点布局层，不持久化
 */
import type {
  GraphProjection,
  LayoutResult,
  MarriageDTO,
  MarriageStatus,
  PersonDTO,
  PositionedEdge,
  PositionedNode,
  RelationshipDTO,
  SiblingKind,
  Union,
} from '../api/types'

export const LAYOUT = {
  nodeWidth: 200,
  nodeHeight: 88,
  siblingGap: 48,
  generationGap: 96,
  partnerGap: 28,
  unionSize: 16,
} as const

const ENDED_STATUSES: MarriageStatus[] = ['divorced', 'widowed', 'ended']

export function isMarriageEnded(status?: MarriageStatus): boolean {
  return status != null && ENDED_STATUSES.includes(status)
}

export interface LayoutOptions {
  focusPersonId?: string
  /** 相对层窗口：默认与 depth=3（up1+down2）对齐 */
  up?: number
  down?: number
}

/**
 * 从 Marriage + PARENT_CHILD 构建 Union 列表。
 * 有 marriageId 的子女进对应 union；否则进「单亲虚拟 union」。
 */
export function buildUnions(
  marriages: MarriageDTO[],
  relationships: RelationshipDTO[],
): Union[] {
  const unions: Union[] = marriages.map((m) => ({
    id: `union:${m.id}`,
    marriageId: m.id,
    status: m.status,
    partnerIds: [...m.partnerIds],
    childIds: [],
  }))

  const byMarriage = new Map(unions.map((u) => [u.marriageId!, u]))
  const synthetic = new Map<string, Union>()

  for (const rel of relationships) {
    if (rel.type !== 'PARENT_CHILD') continue
    if (rel.marriageId && byMarriage.has(rel.marriageId)) {
      const u = byMarriage.get(rel.marriageId)!
      if (!u.childIds.includes(rel.childId)) u.childIds.push(rel.childId)
      continue
    }
    // 单亲虚拟 union：按 parent 分组
    const key = `single:${rel.parentId}`
    let u = synthetic.get(key)
    if (!u) {
      u = {
        id: `union:synthetic:${rel.parentId}`,
        partnerIds: [rel.parentId],
        childIds: [],
      }
      synthetic.set(key, u)
      unions.push(u)
    }
    if (!u.childIds.includes(rel.childId)) u.childIds.push(rel.childId)
  }

  return unions
}

/** BFS 相对焦点层号：焦点=0，父母负，子女正 */
export function computeRelativeLayers(
  focusPersonId: string,
  relationships: RelationshipDTO[],
  personIds: Set<string>,
): Map<string, number> {
  const layers = new Map<string, number>()
  if (!personIds.has(focusPersonId)) return layers
  layers.set(focusPersonId, 0)

  const parentsOf = new Map<string, string[]>()
  const childrenOf = new Map<string, string[]>()
  for (const r of relationships) {
    if (r.type !== 'PARENT_CHILD') continue
    if (!parentsOf.has(r.childId)) parentsOf.set(r.childId, [])
    parentsOf.get(r.childId)!.push(r.parentId)
    if (!childrenOf.has(r.parentId)) childrenOf.set(r.parentId, [])
    childrenOf.get(r.parentId)!.push(r.childId)
  }

  const queue: string[] = [focusPersonId]
  while (queue.length) {
    const id = queue.shift()!
    const layer = layers.get(id)!
    for (const p of parentsOf.get(id) ?? []) {
      if (!personIds.has(p) || layers.has(p)) continue
      layers.set(p, layer - 1)
      queue.push(p)
    }
    for (const c of childrenOf.get(id) ?? []) {
      if (!personIds.has(c) || layers.has(c)) continue
      layers.set(c, layer + 1)
      queue.push(c)
    }
  }

  // 配偶同层：未入层的配偶跟随已布局伙伴
  // （在 layout 主流程里用 marriages 补）
  return layers
}

function attachSpouseLayers(
  layers: Map<string, number>,
  marriages: MarriageDTO[],
  personIds: Set<string>,
): void {
  let changed = true
  while (changed) {
    changed = false
    for (const m of marriages) {
      const [a, b] = m.partnerIds
      if (!personIds.has(a) || !personIds.has(b)) continue
      const la = layers.get(a)
      const lb = layers.get(b)
      if (la != null && lb == null) {
        layers.set(b, la)
        changed = true
      } else if (lb != null && la == null) {
        layers.set(a, lb)
        changed = true
      }
    }
  }
}

/**
 * 纯函数布局入口。
 */
export function layoutUnionGraph(
  projection: Pick<
    GraphProjection,
    'persons' | 'marriages' | 'relationships' | 'rootPersonId'
  >,
  options: LayoutOptions = {},
): LayoutResult {
  const focusId = options.focusPersonId ?? projection.rootPersonId
  const up = options.up ?? 1
  const down = options.down ?? 2

  const personById = new Map(projection.persons.map((p) => [p.id, p]))
  const personIds = new Set(personById.keys())

  const unions = buildUnions(projection.marriages, projection.relationships)
  const layers = computeRelativeLayers(
    focusId,
    projection.relationships,
    personIds,
  )
  attachSpouseLayers(layers, projection.marriages, personIds)

  // 未连通节点放到远离焦点的层，仍渲染（演示完整 fixture）
  for (const id of personIds) {
    if (!layers.has(id)) layers.set(id, 99)
  }

  const inWindow = (layer: number) =>
    layer === 99 || (layer >= -up && layer <= down)

  const visiblePersons = [...personIds].filter((id) =>
    inWindow(layers.get(id) ?? 99),
  )
  const visibleSet = new Set(visiblePersons)

  const visibleUnions = unions.filter(
    (u) =>
      u.partnerIds.some((pid) => visibleSet.has(pid)) ||
      u.childIds.some((cid) => visibleSet.has(cid)),
  )

  // —— 坐标：按层分组，层内按 union 簇排布 ——
  const byLayer = new Map<number, string[]>()
  for (const id of visiblePersons) {
    const L = layers.get(id)!
    if (!byLayer.has(L)) byLayer.set(L, [])
    byLayer.get(L)!.push(id)
  }

  // 稳定排序：有 marriage 的按 startedAt；否则按 id
  const marriageOrder = new Map(
    projection.marriages.map((m, i) => [m.id, m.startedAt ?? String(i)]),
  )

  const personPos = new Map<string, { x: number; y: number }>()
  const unionPos = new Map<string, { x: number; y: number; layer: number }>()

  const sortedLayers = [...byLayer.keys()].sort((a, b) => a - b)

  for (const layer of sortedLayers) {
    const ids = byLayer.get(layer)!
    // 将同层人按「所属主 union」聚类：先排有婚姻的对，再单身
    const placed = new Set<string>()
    const row: string[] = []

    const layerUnions = visibleUnions
      .filter((u) => u.partnerIds.every((p) => layers.get(p) === layer))
      .sort((a, b) => {
        const oa = a.marriageId ? marriageOrder.get(a.marriageId) ?? a.id : a.id
        const ob = b.marriageId ? marriageOrder.get(b.marriageId) ?? b.id : b.id
        return String(oa).localeCompare(String(ob))
      })

    for (const u of layerUnions) {
      for (const pid of u.partnerIds) {
        if (visibleSet.has(pid) && !placed.has(pid)) {
          row.push(pid)
          placed.add(pid)
        }
      }
    }
    for (const id of ids.sort()) {
      if (!placed.has(id)) {
        row.push(id)
        placed.add(id)
      }
    }

    const y = layer * (LAYOUT.nodeHeight + LAYOUT.generationGap)
    let x = 0
    for (let i = 0; i < row.length; i++) {
      const id = row[i]!
      // 若与前一人是同一 union 的配偶，用 partnerGap；否则 siblingGap
      if (i > 0) {
        const prev = row[i - 1]!
        const sameUnion = layerUnions.some(
          (u) => u.partnerIds.includes(prev) && u.partnerIds.includes(id),
        )
        x += sameUnion
          ? LAYOUT.nodeWidth + LAYOUT.partnerGap
          : LAYOUT.nodeWidth + LAYOUT.siblingGap
      }
      personPos.set(id, { x, y })
    }

    // union 锚点：配偶中点（或单亲下方偏移）
    for (const u of visibleUnions) {
      const partners = u.partnerIds.filter((p) => personPos.has(p))
      if (partners.length === 0) continue
      const partnerLayers = partners.map((p) => layers.get(p)!)
      const uLayer = Math.min(...partnerLayers)
      if (uLayer !== layer && partners.length > 1) continue
      if (partners.length === 1 && uLayer !== layer) continue

      const xs = partners.map((p) => personPos.get(p)!.x + LAYOUT.nodeWidth / 2)
      const ys = partners.map((p) => personPos.get(p)!.y + LAYOUT.nodeHeight)
      const ux = xs.reduce((a, b) => a + b, 0) / xs.length
      const uy =
        partners.length === 1
          ? ys[0]! + 8
          : Math.max(...ys) - LAYOUT.nodeHeight / 2 + LAYOUT.unionSize / 2
      // 仅在本层写入一次
      if (!unionPos.has(u.id) || uLayer === layer) {
        unionPos.set(u.id, {
          x: ux - LAYOUT.unionSize / 2,
          y: partners.length === 1 ? uy : (ys[0]! + ys[ys.length - 1]!) / 2,
          layer: uLayer,
        })
      }
    }
  }

  // 子女层：微调未参与配偶排布的位置——已在 byLayer 排好

  const nodes: PositionedNode[] = []
  for (const id of visiblePersons) {
    const pos = personPos.get(id)
    if (!pos) continue
    const person = personById.get(id)!
    nodes.push({
      id: `person:${id}`,
      kind: 'person',
      layer: layers.get(id)!,
      x: pos.x,
      y: pos.y,
      personId: id,
      data: { person, isFocus: id === focusId },
    })
  }

  for (const u of visibleUnions) {
    const pos = unionPos.get(u.id)
    if (!pos) {
      // 兜底：子女与父母中点
      const refs = [...u.partnerIds, ...u.childIds]
        .map((pid) => personPos.get(pid))
        .filter(Boolean) as { x: number; y: number }[]
      if (refs.length === 0) continue
      const ax = refs.reduce((s, p) => s + p.x, 0) / refs.length
      const ay = refs.reduce((s, p) => s + p.y, 0) / refs.length
      unionPos.set(u.id, { x: ax, y: ay, layer: 0 })
    }
    const p = unionPos.get(u.id)!
    nodes.push({
      id: u.id,
      kind: 'union',
      layer: p.layer,
      x: p.x,
      y: p.y,
      unionId: u.id,
      data: {
        union: u,
        ended: isMarriageEnded(u.status),
        marriageStatus: u.status,
      },
    })
  }

  const edges: PositionedEdge[] = []

  // 配偶归属：person → union；已结束标 ended
  for (const u of visibleUnions) {
    const ended = isMarriageEnded(u.status)
    for (const pid of u.partnerIds) {
      if (!visibleSet.has(pid)) continue
      if (!unionPos.has(u.id) && !nodes.find((n) => n.id === u.id)) continue
      edges.push({
        id: `e:partner:${u.id}:${pid}`,
        source: `person:${pid}`,
        target: u.id,
        kind: 'partner',
        data: {
          ended,
          marriageStatus: u.status,
        },
      })
    }
    // union 横杆语义边（自环占位，UI 可用 union 节点样式表达）
    if (u.partnerIds.length >= 2 && u.marriageId) {
      edges.push({
        id: `e:bar:${u.id}`,
        source: `person:${u.partnerIds[0]}`,
        target: `person:${u.partnerIds[1]}`,
        kind: 'union-bar',
        data: {
          ended,
          marriageStatus: u.status,
        },
      })
    }
    for (const cid of u.childIds) {
      if (!visibleSet.has(cid)) continue
      const rel = projection.relationships.find(
        (r) =>
          r.childId === cid &&
          (r.marriageId === u.marriageId ||
            (!r.marriageId && u.partnerIds.includes(r.parentId))),
      )
      edges.push({
        id: `e:pc:${u.id}:${cid}`,
        source: u.id,
        target: `person:${cid}`,
        kind: 'parent-child',
        data: {
          ended: false,
          subtype: rel?.subtype ?? 'biological',
        },
      })
    }
  }

  return { nodes, edges, unions: visibleUnions }
}

/** 供详情抽屉派生父母 / 配偶 / 子女 */
export function deriveKinForPerson(
  personId: string,
  projection: Pick<GraphProjection, 'persons' | 'marriages' | 'relationships'>,
): {
  parents: { person: PersonDTO; role?: string; subtype?: string; relationshipId: string }[]
  spouses: { person: PersonDTO; status: MarriageStatus; marriageId: string }[]
  children: { person: PersonDTO; role?: string; subtype?: string; relationshipId: string }[]
} {
  const byId = new Map(projection.persons.map((p) => [p.id, p]))
  const parents: {
    person: PersonDTO
    role?: string
    subtype?: string
    relationshipId: string
  }[] = []
  const children: {
    person: PersonDTO
    role?: string
    subtype?: string
    relationshipId: string
  }[] = []

  for (const r of projection.relationships) {
    if (r.type !== 'PARENT_CHILD') continue
    if (r.childId === personId) {
      const p = byId.get(r.parentId)
      if (p) parents.push({ person: p, role: r.role, subtype: r.subtype, relationshipId: r.id })
    }
    if (r.parentId === personId) {
      const c = byId.get(r.childId)
      if (c) children.push({ person: c, role: r.role, subtype: r.subtype, relationshipId: r.id })
    }
  }

  const spouses: {
    person: PersonDTO
    status: MarriageStatus
    marriageId: string
  }[] = []
  for (const m of projection.marriages) {
    const idx = m.partnerIds.indexOf(personId)
    if (idx < 0) continue
    const otherId = m.partnerIds[idx === 0 ? 1 : 0]!
    const other = byId.get(otherId)
    if (other) {
      spouses.push({ person: other, status: m.status, marriageId: m.id })
    }
  }

  return { parents, spouses, children }
}

/** Sibling kind labels for Chinese UI */
export const SIBLING_KIND_LABEL: Record<SiblingKind, string> = {
  full: '同胞',
  paternal_half: '同父异母',
  maternal_half: '同母异父',
}

/**
 * Derive siblings for the selected person from graph.siblings.
 * Filters sibling pairs where personId or siblingId matches the selected person,
 * resolves display names from persons when present.
 */
export function deriveSiblingsForPerson(
  personId: string,
  projection: Pick<GraphProjection, 'persons' | 'siblings'>,
): {
  person: PersonDTO | null
  siblingId: string
  kind: SiblingKind
  sharedParentIds: string[]
}[] {
  if (!projection.siblings?.length) return []

  const byId = new Map(projection.persons.map((p) => [p.id, p]))
  const results: {
    person: PersonDTO | null
    siblingId: string
    kind: SiblingKind
    sharedParentIds: string[]
  }[] = []

  for (const sib of projection.siblings) {
    let otherId: string | null = null
    if (sib.personId === personId) {
      otherId = sib.siblingId
    } else if (sib.siblingId === personId) {
      otherId = sib.personId
    }
    if (otherId) {
      results.push({
        person: byId.get(otherId) ?? null,
        siblingId: otherId,
        kind: sib.kind,
        sharedParentIds: sib.sharedParentIds,
      })
    }
  }

  return results
}
