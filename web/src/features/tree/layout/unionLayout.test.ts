import { describe, expect, it } from 'vitest'
import { chenDivorceRemarriageFixture } from '../api/fixture'
import type {
  GraphProjection,
  MarriageDTO,
  PersonDTO,
  RelationshipDTO,
} from '../api/types'
import {
  buildUnions,
  deriveSiblingsForPerson,
  isMarriageEnded,
  layoutUnionGraph,
  SIBLING_KIND_LABEL,
} from './unionLayout'

function projectionOf(
  persons: PersonDTO[],
  marriages: MarriageDTO[],
  relationships: RelationshipDTO[],
  rootPersonId: string,
): GraphProjection {
  return {
    familyId: 'test',
    rootPersonId,
    depth: 3,
    truncated: false,
    persons,
    marriages,
    relationships,
  }
}

describe('buildUnions / layoutUnionGraph', () => {
  it('离婚再婚：两段 union，Person 节点唯一，已结束婚姻 edge.data.ended', () => {
    const result = layoutUnionGraph(chenDivorceRemarriageFixture, {
      focusPersonId: 'p-chen-jianguo',
    })

    const personNodes = result.nodes.filter((n) => n.kind === 'person')
    const personIds = personNodes.map((n) => n.personId)
    expect(new Set(personIds).size).toBe(personIds.length)

    const jianguoNodes = personNodes.filter(
      (n) => n.personId === 'p-chen-jianguo',
    )
    expect(jianguoNodes).toHaveLength(1)

    const unions = result.unions.filter((u) =>
      u.partnerIds.includes('p-chen-jianguo'),
    )
    expect(unions.length).toBeGreaterThanOrEqual(2)

    const divorced = unions.find((u) => u.marriageId === 'm-jianguo-li')
    const active = unions.find((u) => u.marriageId === 'm-jianguo-wang')
    expect(divorced?.status).toBe('divorced')
    expect(active?.status).toBe('active')
    expect(isMarriageEnded(divorced?.status)).toBe(true)
    expect(isMarriageEnded(active?.status)).toBe(false)

    const endedEdges = result.edges.filter((e) => e.data?.ended === true)
    expect(endedEdges.length).toBeGreaterThan(0)
    expect(
      endedEdges.some(
        (e) =>
          e.kind === 'union-bar' ||
          e.kind === 'partner',
      ),
    ).toBe(true)

    // 前婚 / 再婚子女仍挂在各自 union
    expect(divorced?.childIds).toContain('p-chen-ming')
    expect(active?.childIds).toEqual(
      expect.arrayContaining(['p-chen-hua', 'p-chen-fang']),
    )
  })

  it('双亲：同一子女可有父+母两条 PARENT_CHILD，均参与布局', () => {
    const persons: PersonDTO[] = [
      { id: 'f', displayName: '父' },
      { id: 'm', displayName: '母' },
      { id: 'c', displayName: '子' },
    ]
    const marriages: MarriageDTO[] = [
      {
        id: 'mar1',
        partnerIds: ['f', 'm'],
        status: 'active',
        startedAt: '2000',
      },
    ]
    const relationships: RelationshipDTO[] = [
      {
        id: 'r1',
        type: 'PARENT_CHILD',
        subtype: 'biological',
        parentId: 'f',
        childId: 'c',
        role: 'father',
        marriageId: 'mar1',
      },
      {
        id: 'r2',
        type: 'PARENT_CHILD',
        subtype: 'biological',
        parentId: 'm',
        childId: 'c',
        role: 'mother',
        marriageId: 'mar1',
      },
    ]
    const proj = projectionOf(persons, marriages, relationships, 'c')
    const unions = buildUnions(marriages, relationships)
    expect(unions).toHaveLength(1)
    expect(unions[0]!.partnerIds).toEqual(['f', 'm'])
    expect(unions[0]!.childIds).toEqual(['c'])

    const layout = layoutUnionGraph(proj, { focusPersonId: 'c' })
    const ids = layout.nodes
      .filter((n) => n.kind === 'person')
      .map((n) => n.personId)
    expect(ids).toEqual(expect.arrayContaining(['f', 'm', 'c']))
    expect(layout.nodes.filter((n) => n.kind === 'person')).toHaveLength(3)

    const childLayer = layout.nodes.find((n) => n.personId === 'c')!.layer
    const fatherLayer = layout.nodes.find((n) => n.personId === 'f')!.layer
    expect(fatherLayer).toBe(childLayer - 1)
  })

  it('多父：同一 child 多条 PARENT_CHILD 全部保留（可来自不同 union）', () => {
    const persons: PersonDTO[] = [
      { id: 'bio-f', displayName: '生父' },
      { id: 'ado-f', displayName: '养父' },
      { id: 'c', displayName: '子女' },
    ]
    const marriages: MarriageDTO[] = []
    const relationships: RelationshipDTO[] = [
      {
        id: 'r-bio',
        type: 'PARENT_CHILD',
        subtype: 'biological',
        parentId: 'bio-f',
        childId: 'c',
        role: 'father',
      },
      {
        id: 'r-ado',
        type: 'PARENT_CHILD',
        subtype: 'adoptive',
        parentId: 'ado-f',
        childId: 'c',
        role: 'father',
      },
    ]
    const unions = buildUnions(marriages, relationships)
    // 两个单亲虚拟 union
    expect(unions).toHaveLength(2)
    expect(unions.every((u) => u.childIds.includes('c'))).toBe(true)

    const layout = layoutUnionGraph(
      projectionOf(persons, marriages, relationships, 'c'),
      { focusPersonId: 'c' },
    )
    const personCount = layout.nodes.filter((n) => n.kind === 'person').length
    expect(personCount).toBe(3)
    const pcEdges = layout.edges.filter((e) => e.kind === 'parent-child')
    expect(pcEdges.length).toBeGreaterThanOrEqual(2)
  })

  it('单亲：无 marriageId 时生成 synthetic union，仅一名 partner', () => {
    const persons: PersonDTO[] = [
      { id: 'p', displayName: '单亲父' },
      { id: 'c', displayName: '子女' },
    ]
    const marriages: MarriageDTO[] = []
    const relationships: RelationshipDTO[] = [
      {
        id: 'r1',
        type: 'PARENT_CHILD',
        subtype: 'biological',
        parentId: 'p',
        childId: 'c',
        role: 'father',
      },
    ]
    const unions = buildUnions(marriages, relationships)
    expect(unions).toHaveLength(1)
    expect(unions[0]!.id).toContain('synthetic')
    expect(unions[0]!.partnerIds).toEqual(['p'])
    expect(unions[0]!.childIds).toEqual(['c'])

    const layout = layoutUnionGraph(
      projectionOf(persons, marriages, relationships, 'p'),
      { focusPersonId: 'p' },
    )
    expect(layout.nodes.filter((n) => n.kind === 'union')).toHaveLength(1)
    expect(
      layout.edges.some(
        (e) => e.kind === 'parent-child' && e.target === 'person:c',
      ),
    ).toBe(true)
  })

  it('fixture 陈明双亲 + 陈单亲单亲链可布局且坐标有限', () => {
    const layout = layoutUnionGraph(chenDivorceRemarriageFixture)
    for (const n of layout.nodes) {
      expect(Number.isFinite(n.x)).toBe(true)
      expect(Number.isFinite(n.y)).toBe(true)
    }
    const ming = layout.nodes.find((n) => n.personId === 'p-chen-ming')
    expect(ming).toBeTruthy()
  })
})

describe('deriveSiblingsForPerson', () => {
  it('returns siblings for selected person from fixture', () => {
    const siblings = deriveSiblingsForPerson('p-chen-hua', chenDivorceRemarriageFixture)
    expect(siblings).toHaveLength(2)

    const fullSibling = siblings.find((s) => s.kind === 'full')
    expect(fullSibling).toBeTruthy()
    expect(fullSibling!.siblingId).toBe('p-chen-fang')
    expect(fullSibling!.person?.displayName).toBe('陈芳')

    const halfSibling = siblings.find((s) => s.kind === 'paternal_half')
    expect(halfSibling).toBeTruthy()
    expect(halfSibling!.siblingId).toBe('p-chen-ming')
    expect(halfSibling!.person?.displayName).toBe('陈明')
  })

  it('returns empty array when person has no siblings', () => {
    const siblings = deriveSiblingsForPerson('p-chen-jianguo', chenDivorceRemarriageFixture)
    expect(siblings).toHaveLength(0)
  })

  it('returns null person when sibling not in persons list (truncation edge)', () => {
    const truncatedFixture = {
      ...chenDivorceRemarriageFixture,
      persons: chenDivorceRemarriageFixture.persons.filter((p) => p.id !== 'p-chen-fang'),
    }
    const siblings = deriveSiblingsForPerson('p-chen-hua', truncatedFixture)
    const fangSibling = siblings.find((s) => s.siblingId === 'p-chen-fang')
    expect(fangSibling).toBeTruthy()
    expect(fangSibling!.person).toBeNull()
  })

  it('SIBLING_KIND_LABEL has correct Chinese labels', () => {
    expect(SIBLING_KIND_LABEL.full).toBe('同胞')
    expect(SIBLING_KIND_LABEL.paternal_half).toBe('同父异母')
    expect(SIBLING_KIND_LABEL.maternal_half).toBe('同母异父')
  })
})
