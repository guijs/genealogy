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
 * POST /api/v1/families/{familyId}/relationships 请求体。
 * Java 端使用 @JsonProperty 注解，字段为 snake_case。
 */
export interface AddRelationshipRequest {
  parent_id: string
  child_id: string
  relationship_type: 'biological' | 'adoptive'
}

export interface AddRelationshipResponse {
  success: boolean
}
