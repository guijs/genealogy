/**
 * GraphProjection — 模拟 GET /api/v1/families/{familyId}/graph 的只读投影契约。
 *
 * 硬规则：
 * - 无 person.parentId（亲子仅来自 relationships）
 * - 禁止整树写回；写路径仅单笔 person / relationship / marriage API
 * - 代数 = 相对焦点的布局层号，不持久化
 *
 * depth 换算注释：
 *   默认视图「上 1 / 下 2」且含焦点层 ⇒ 请求 depth=3
 *   （depth = up + down；焦点层计入窗内，即 up1+down2+焦点 ⇒ depth=3）
 */

export type Gender = 'male' | 'female' | 'unknown' | 'unspecified'

/** 婚姻存续状态；已结束（离婚/丧偶等）仍出现在投影中，UI 用虚线表示 */
export type MarriageStatus = 'active' | 'divorced' | 'widowed' | 'ended'

export type ParentChildSubtype = 'biological' | 'adoptive'

export type ParentRole = 'father' | 'mother' | 'parent'

/** Sibling kind for derived sibling relationships */
export type SiblingKind = 'full' | 'paternal_half' | 'maternal_half'

/**
 * Derived sibling relationship.
 * Siblings are derived-only (never a writable fact) based on shared biological parents.
 * P0 derivation uses only biological parent edges (biological_father / biological_mother).
 * - Share both biological parents → full
 * - Share only biological father → paternal_half
 * - Share only biological mother → maternal_half
 * Pairs are emitted once with canonicalized IDs (personId < siblingId lexicographically).
 */
export interface DerivedSibling {
  personId: string
  siblingId: string
  kind: SiblingKind
  sharedParentIds: string[]
}

export interface PersonDTO {
  id: string
  displayName: string
  gender?: Gender
  birthYear?: number | null
  deathYear?: number | null
  /** 是否已故；也可由 deathYear 推导 */
  deceased?: boolean
}

export interface MarriageDTO {
  id: string
  /** 配偶双方 personId（P0 二人婚姻） */
  partnerIds: [string, string]
  status: MarriageStatus
  startedAt?: string | null
  endedAt?: string | null
  endedReason?: string | null
}

export interface RelationshipDTO {
  id: string
  type: 'PARENT_CHILD'
  subtype: ParentChildSubtype
  parentId: string
  childId: string
  role?: ParentRole
  /** 可选：子女归属哪一段婚姻 union；缺省则进单亲虚拟 union */
  marriageId?: string | null
}

/**
 * 家族网络只读投影（非单根树）。
 */
export interface GraphProjection {
  familyId: string
  rootPersonId: string
  /**
   * 展开深度。默认 3 ⇔ 相对焦点 up=1 + down=2（含焦点层）。
   * 见文件头 depth 换算注释。
   */
  depth: number
  /** 因 depth / 人数上限截断时必须为 true，禁止静默丢点 */
  truncated: boolean
  truncateReason?: string
  persons: PersonDTO[]
  /** 含存续与已结束婚姻；离婚不删边 */
  marriages: MarriageDTO[]
  relationships: RelationshipDTO[]
  /**
   * Derived sibling pairs based on shared biological parents (AP-R9).
   * Optional; omitted when empty or null.
   */
  siblings?: DerivedSibling[]
}

/** 布局中间态：一段配偶或单亲虚拟 union */
export interface Union {
  id: string
  marriageId?: string
  status?: MarriageStatus
  partnerIds: string[]
  childIds: string[]
}

export type TreeNodeKind = 'person' | 'union'

export interface PositionedNode {
  id: string
  kind: TreeNodeKind
  /** 相对焦点的布局层：焦点=0，上代负，下代正；不持久化 */
  layer: number
  x: number
  y: number
  personId?: string
  unionId?: string
  data?: Record<string, unknown>
}

export interface PositionedEdge {
  id: string
  source: string
  target: string
  kind: 'partner' | 'parent-child' | 'union-bar'
  /** 已结束婚姻在 edge data 里标 ended */
  data?: {
    ended?: boolean
    marriageStatus?: MarriageStatus
    subtype?: ParentChildSubtype
    [key: string]: unknown
  }
}

export interface LayoutResult {
  nodes: PositionedNode[]
  edges: PositionedEdge[]
  unions: Union[]
}

/**
 * Java RelationType.fromString 接受的关系类型字符串。
 * 格式为 subtype_role（如 biological_father）。
 */
export type RelationshipType =
  | 'biological_father'
  | 'biological_mother'
  | 'adoptive_father'
  | 'adoptive_mother'

/**
 * POST /api/v1/families/{familyId}/relationships 请求体。
 * Java 端使用 @JsonProperty 注解，字段为 snake_case。
 * relationship_type 必须为 Java RelationType.fromString 支持的值。
 */
export interface AddRelationshipRequest {
  parent_id: string
  child_id: string
  relationship_type: RelationshipType
}

export interface AddRelationshipResponse {
  success: boolean
}

/**
 * POST /api/v1/families/{familyId}/relationships/{id}/dissolve
 * POST /api/v1/families/{familyId}/relationships/{id}/restore
 * Response body: { id: string, dissolved: boolean }
 */
export interface DissolveRestoreResponse {
  id: string
  dissolved: boolean
}

/**
 * 客户端缓存的已解除亲子关系信息（用于恢复面板）
 * 因为 /graph 不返回已解除的关系，需要客户端本地存储
 */
export interface DissolvedRelationship {
  id: string
  parentId: string
  childId: string
  parentDisplayName: string
  childDisplayName: string
  subtype?: string
  role?: string
}

/**
 * P1: Ego-relative generation layer projection.
 * GET /api/v1/families/{familyId}/generations?focusPersonId={uuid}
 *
 * focusPersonId is optional:
 * - If provided and valid in family: use as Ego
 * - If omitted/empty: fallback to earliest created non-hidden person in family
 * - If family has zero non-hidden persons: 404 "no persons in family"
 *
 * Client selection strategy (not enforced by API):
 * - "本人节点" (self node) if user has one
 * - "会话上次焦点" (session's last focus) if available
 * - Otherwise omit and let backend resolve to earliest person
 *
 * Generation rules:
 * - Ego is at generation index 0
 * - Parents via biological edges → -1, grandparents → -2, etc.
 * - Children via biological edges → +1, grandchildren → +2, etc.
 * - Adoptive edges do NOT change generation (never used for climb)
 * - Dissolved edges are excluded
 * - Hidden persons are excluded
 * - Spouses are not included via marriage alone (only bio-reachable persons)
 */

export interface GenerationPersonDTO {
  id: string
  displayName: string
  /** True if person is reachable via paths with conflicting generation indices */
  conflict?: boolean
}

export interface GenerationDTO {
  index: number
  persons: GenerationPersonDTO[]
}

export interface GenerationsProjection {
  familyId: string
  focusPersonId: string
  /** Generations sorted by index ascending */
  generations: GenerationDTO[]
}

/**
 * 世系投影 — GET /api/v1/families/{familyId}/lineage
 * 
 * 从始祖(progenitor)向下、仅通过非解除的生物学亲子关系遍历。
 * 始祖为第1世，其生物学子女为第2世，以此类推。
 * 
 * 规则：
 * - 仅非解除的 biological 边参与遍历
 * - 养子关系(adoptive)不参与世系层级
 * - Hidden人员不出现在世系中
 * - 未连接到始祖的人员不出现
 * - 多路径到达同一人员且世次不一致时，标记 conflict: true
 * - 配偶不创建世系层
 */
export interface LineagePersonDTO {
  id: string
  display_name: string
  conflict: boolean
}

export interface LineageGenerationDTO {
  index: number
  persons: LineagePersonDTO[]
  /** Generation name (字辈) for this generation, null if not available or beyond sequence */
  generation_name: string | null
}

export interface LineageResponse {
  family_id: string
  progenitor_person_id: string | null
  generations: LineageGenerationDTO[]
  /** Alignment mode for generation names */
  generation_name_align: GenerationNameAlign
}

export interface SetProgenitorRequest {
  person_id: string | null
}

export interface SetProgenitorResponse {
  progenitor_person_id: string | null
}

/**
 * P1: Generation names (字辈) alignment mode.
 * - A (default): name index k (1-based) ↔ lineage generation k+1
 *   (第1世始祖无字辈字；第2世用第1个字…)
 * - B: name index k ↔ lineage generation k
 *   (第1世用第1个字)
 */
export type GenerationNameAlign = 'A' | 'B'

/**
 * GET /api/v1/families/{familyId}/generation-names
 * PUT /api/v1/families/{familyId}/generation-names
 */
export interface GenerationNamesRequest {
  generation_names: string[]
  generation_name_align?: GenerationNameAlign
}

export interface GenerationNamesResponse {
  generation_names: string[] | null
  generation_name_align: GenerationNameAlign
}
