/**
 * 家族图只读 API client 切换点。
 *
 * - 默认：MOCK fixture（待换真 API）
 * - 切换：VITE_USE_GRAPH_API=true 或设置了 VITE_GRAPH_API_BASE
 * - 真路径必须带 familyId（B1）：GET /api/v1/families/{familyId}/graph
 *   不接受无 family 的 person-only 路由
 * - 真 API 需认证：Authorization: Bearer <token>
 *
 * P0 约束：
 * - 无 asOf（历史时点视图 → P1）
 * - 响应可含 ended unions（离婚/丧偶等仍在投影，UI 虚线）
 * - 禁整树写回；写路径仅单笔 person / relationship / marriage
 */
import {
  DEMO_FAMILY_ID,
  DEMO_ROOT_PERSON_ID,
  fetchGraphFixture,
} from './fixture'
import type {
  AddRelationshipRequest,
  AddRelationshipResponse,
  GraphProjection,
  RelationshipType,
} from './types'
import { getAuthHeaders, getAuthHeadersWithContentType } from './auth'

export interface FetchFamilyGraphParams {
  familyId: string
  rootPersonId: string
  depth: number
}

/** 默认演示深度：up1 + down2 + 焦点 ⇒ depth=3 */
export const DEFAULT_GRAPH_DEPTH = 3

/**
 * 是否走真 API。
 * - VITE_USE_GRAPH_API === 'true' → 真 API
 * - 或设置了非空 VITE_GRAPH_API_BASE → 真 API
 * - 否则 MOCK fixture（默认）
 */
export function isUsingGraphApi(): boolean {
  const useFlag = import.meta.env.VITE_USE_GRAPH_API === 'true'
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return useFlag || Boolean(base)
}

function graphApiBase(): string {
  const base = (import.meta.env.VITE_GRAPH_API_BASE as string | undefined)?.trim()
  return base ? base.replace(/\/$/, '') : ''
}

/**
 * 拉取家族图只读投影。
 *
 * MOCK（默认）：返回 fixture，标注清晰便于日后替换。
 * 真 API：GET `${base}/api/v1/families/${familyId}/graph?rootPersonId=&depth=`
 */
export async function fetchFamilyGraph(
  params: FetchFamilyGraphParams,
): Promise<GraphProjection> {
  const { familyId, rootPersonId, depth } = params

  if (!isUsingGraphApi()) {
    // --- MOCK / 待换真 API ---
    // 演示仍走 typed fixture；rootPersonId/depth 由 fixture 自带，不请求网络。
    return fetchGraphFixture(familyId)
  }

  // --- 真 API（B1：必须带 familyId）---
  // P0 无 asOf；响应可含 ended unions；禁整树写回。
  if (!familyId) {
    throw new Error(
      'fetchFamilyGraph: familyId is required (B1 — no person-only graph route)',
    )
  }

  const base = graphApiBase()
  const qs = new URLSearchParams({
    rootPersonId,
    depth: String(depth),
  })
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/graph?${qs}`

  const res = await fetch(url, { headers: getAuthHeaders() })
  if (!res.ok) {
    throw new Error(
      `GET family graph failed: ${res.status} ${res.statusText} (${url})`,
    )
  }
  return (await res.json()) as GraphProjection
}

/** 演示用默认参数（保留 fixture 的 familyId / root） */
export const DEMO_GRAPH_PARAMS: FetchFamilyGraphParams = {
  familyId: DEMO_FAMILY_ID,
  rootPersonId: DEMO_ROOT_PERSON_ID,
  depth: DEFAULT_GRAPH_DEPTH,
}

export interface AddRelationshipParams {
  familyId: string
  parentId: string
  childId: string
  /** Java RelationType.fromString 格式：biological_father | biological_mother | adoptive_father | adoptive_mother */
  relationshipType: RelationshipType
}

/**
 * 添加亲子关系。
 *
 * POST /api/v1/families/{familyId}/relationships
 * Body: { parent_id, child_id, relationship_type } (snake_case)
 * Header: X-User-Id
 *
 * relationship_type 必须为 Java RelationType.fromString 支持的值：
 *   biological_father | biological_mother | adoptive_father | adoptive_mother
 *
 * 仅在使用真 API 时可用。调用后需 reloadGraph 刷新投影。
 * @throws Error 若未开启真 API 或请求失败
 */
export async function addRelationship(
  params: AddRelationshipParams,
): Promise<AddRelationshipResponse> {
  if (!isUsingGraphApi()) {
    throw new Error(
      'addRelationship requires real API. Set VITE_USE_GRAPH_API=true or VITE_GRAPH_API_BASE.',
    )
  }

  const { familyId, parentId, childId, relationshipType } = params
  if (!familyId) {
    throw new Error('addRelationship: familyId is required')
  }

  const base = graphApiBase()
  const url = `${base}/api/v1/families/${encodeURIComponent(familyId)}/relationships`

  const body: AddRelationshipRequest = {
    parent_id: parentId,
    child_id: childId,
    relationship_type: relationshipType,
  }

  const res = await fetch(url, {
    method: 'POST',
    headers: getAuthHeadersWithContentType(),
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    const errText = await res.text().catch(() => '')
    throw new Error(
      `POST relationship failed: ${res.status} ${res.statusText} — ${errText}`,
    )
  }
  return (await res.json()) as AddRelationshipResponse
}
