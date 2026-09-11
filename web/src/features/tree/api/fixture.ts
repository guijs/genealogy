/**
 * 陈父离婚再婚演示数据 — 模拟 GET /api/v1/families/{familyId}/graph
 *
 * 场景：
 * - 陈建国 与 李秀英 离婚（前婚仍在投影，虚线）
 * - 陈建国 再婚 王美兰（存续）
 * - 前婚子女 陈明；再婚子女 陈华、陈芳
 * - 陈明有双亲；陈华同父异母
 * - 另含：陈祖父/陈祖母（焦点上一代）、单亲示例边
 */
import type { GraphProjection } from './types'

export const DEMO_FAMILY_ID = 'fam-chen-demo'
export const DEMO_ROOT_PERSON_ID = 'p-chen-jianguo'

export const chenDivorceRemarriageFixture: GraphProjection = {
  familyId: DEMO_FAMILY_ID,
  rootPersonId: DEMO_ROOT_PERSON_ID,
  // depth=3 ⇔ up1+down2+焦点（见 types.ts 注释）
  depth: 3,
  truncated: true,
  truncateReason: '已达展开上限',
  persons: [
    {
      id: 'p-chen-yeye',
      displayName: '陈志远',
      gender: 'male',
      birthYear: 1928,
      deathYear: 2005,
      deceased: true,
    },
    {
      id: 'p-chen-nainai',
      displayName: '赵桂兰',
      gender: 'female',
      birthYear: 1930,
      deathYear: 2012,
      deceased: true,
    },
    {
      id: 'p-chen-jianguo',
      displayName: '陈建国',
      gender: 'male',
      birthYear: 1958,
    },
    {
      id: 'p-li-xiuying',
      displayName: '李秀英',
      gender: 'female',
      birthYear: 1960,
    },
    {
      id: 'p-wang-meilan',
      displayName: '王美兰',
      gender: 'female',
      birthYear: 1965,
    },
    {
      id: 'p-chen-ming',
      displayName: '陈明',
      gender: 'male',
      birthYear: 1982,
    },
    {
      id: 'p-chen-hua',
      displayName: '陈华',
      gender: 'male',
      birthYear: 1990,
    },
    {
      id: 'p-chen-fang',
      displayName: '陈芳',
      gender: 'female',
      birthYear: 1993,
    },
    {
      id: 'p-chen-danqin',
      displayName: '陈单亲',
      gender: 'male',
      birthYear: 2000,
    },
  ],
  marriages: [
    {
      id: 'm-yeye-nainai',
      partnerIds: ['p-chen-yeye', 'p-chen-nainai'],
      status: 'widowed',
      startedAt: '1950',
      endedAt: '2005',
      endedReason: 'death',
    },
    {
      id: 'm-jianguo-li',
      partnerIds: ['p-chen-jianguo', 'p-li-xiuying'],
      status: 'divorced',
      startedAt: '1980',
      endedAt: '1988',
      endedReason: 'divorce',
    },
    {
      id: 'm-jianguo-wang',
      partnerIds: ['p-chen-jianguo', 'p-wang-meilan'],
      status: 'active',
      startedAt: '1989',
    },
  ],
  relationships: [
    // 陈建国 ← 陈志远 / 赵桂兰
    {
      id: 'r-yeye-jianguo',
      type: 'PARENT_CHILD',
      subtype: 'biological',
      parentId: 'p-chen-yeye',
      childId: 'p-chen-jianguo',
      role: 'father',
      marriageId: 'm-yeye-nainai',
    },
    {
      id: 'r-nainai-jianguo',
      type: 'PARENT_CHILD',
      subtype: 'biological',
      parentId: 'p-chen-nainai',
      childId: 'p-chen-jianguo',
      role: 'mother',
      marriageId: 'm-yeye-nainai',
    },
    // 前婚子女 陈明
    {
      id: 'r-jianguo-ming',
      type: 'PARENT_CHILD',
      subtype: 'biological',
      parentId: 'p-chen-jianguo',
      childId: 'p-chen-ming',
      role: 'father',
      marriageId: 'm-jianguo-li',
    },
    {
      id: 'r-li-ming',
      type: 'PARENT_CHILD',
      subtype: 'biological',
      parentId: 'p-li-xiuying',
      childId: 'p-chen-ming',
      role: 'mother',
      marriageId: 'm-jianguo-li',
    },
    // 再婚子女 陈华、陈芳
    {
      id: 'r-jianguo-hua',
      type: 'PARENT_CHILD',
      subtype: 'biological',
      parentId: 'p-chen-jianguo',
      childId: 'p-chen-hua',
      role: 'father',
      marriageId: 'm-jianguo-wang',
    },
    {
      id: 'r-wang-hua',
      type: 'PARENT_CHILD',
      subtype: 'biological',
      parentId: 'p-wang-meilan',
      childId: 'p-chen-hua',
      role: 'mother',
      marriageId: 'm-jianguo-wang',
    },
    {
      id: 'r-jianguo-fang',
      type: 'PARENT_CHILD',
      subtype: 'biological',
      parentId: 'p-chen-jianguo',
      childId: 'p-chen-fang',
      role: 'father',
      marriageId: 'm-jianguo-wang',
    },
    {
      id: 'r-wang-fang',
      type: 'PARENT_CHILD',
      subtype: 'biological',
      parentId: 'p-wang-meilan',
      childId: 'p-chen-fang',
      role: 'mother',
      marriageId: 'm-jianguo-wang',
    },
    // 单亲：仅陈明 → 陈单亲（无 marriageId → 虚拟单亲 union）
    {
      id: 'r-ming-danqin',
      type: 'PARENT_CHILD',
      subtype: 'biological',
      parentId: 'p-chen-ming',
      childId: 'p-chen-danqin',
      role: 'father',
    },
  ],
}

/** 模拟异步拉取 graph 投影 */
export async function fetchGraphFixture(
  _familyId: string = DEMO_FAMILY_ID,
): Promise<GraphProjection> {
  return structuredClone(chenDivorceRemarriageFixture)
}
