import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useTreeViewStore } from './treeViewStore'

const mockLocalStorage = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => { store[key] = value },
    removeItem: (key: string) => { delete store[key] },
    clear: () => { store = {} },
  }
})()

describe('treeViewStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.stubGlobal('localStorage', mockLocalStorage)
    mockLocalStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('isPersonInGraph', () => {
    it('returns false when graph is null', () => {
      const store = useTreeViewStore()
      expect(store.isPersonInGraph('any-id')).toBe(false)
    })

    it('returns true when person is in graph.persons', () => {
      const store = useTreeViewStore()
      store.$patch({
        graph: {
          familyId: 'family-1',
          rootPersonId: 'person-1',
          depth: 3,
          truncated: false,
          persons: [
            { id: 'person-1', displayName: 'Test Person' },
            { id: 'person-2', displayName: 'Another Person' },
          ],
          marriages: [],
          relationships: [],
        },
      })
      expect(store.isPersonInGraph('person-1')).toBe(true)
      expect(store.isPersonInGraph('person-2')).toBe(true)
    })

    it('returns false when person is NOT in graph.persons', () => {
      const store = useTreeViewStore()
      store.$patch({
        graph: {
          familyId: 'family-1',
          rootPersonId: 'person-1',
          depth: 3,
          truncated: false,
          persons: [
            { id: 'person-1', displayName: 'Test Person' },
          ],
          marriages: [],
          relationships: [],
        },
      })
      expect(store.isPersonInGraph('person-outside-window')).toBe(false)
    })
  })

  describe('selectPersonWithFocus', () => {
    it('calls selectPerson directly when person is in graph', async () => {
      const store = useTreeViewStore()
      store.$patch({
        graph: {
          familyId: 'family-1',
          rootPersonId: 'person-1',
          depth: 3,
          truncated: false,
          persons: [
            { id: 'person-1', displayName: 'Test Person' },
          ],
          marriages: [],
          relationships: [],
        },
      })

      await store.selectPersonWithFocus('person-1')

      expect(store.selectedPersonId).toBe('person-1')
      expect(store.drawerOpen).toBe(true)
    })

    it('regression: person in generations but not in graph window → opens drawer after reload', async () => {
      const store = useTreeViewStore()
      
      const mockFetch = vi.fn()
      vi.stubGlobal('fetch', mockFetch)
      mockLocalStorage.setItem('auth_token', 'test-token')
      
      import.meta.env.VITE_USE_GRAPH_API = 'true'
      import.meta.env.VITE_GRAPH_API_BASE = 'http://localhost:8080'

      store.$patch({
        graph: {
          familyId: 'family-1',
          rootPersonId: 'person-1',
          depth: 3,
          truncated: true,
          persons: [
            { id: 'person-1', displayName: 'Test Person' },
          ],
          marriages: [],
          relationships: [],
        },
        usingGraphApi: true,
        currentFamilyId: 'family-1',
      })

      const personOutsideWindow = 'person-outside-current-window'
      expect(store.isPersonInGraph(personOutsideWindow)).toBe(false)

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          familyId: 'family-1',
          rootPersonId: personOutsideWindow,
          depth: 3,
          truncated: false,
          persons: [
            { id: personOutsideWindow, displayName: 'Person Outside Window' },
            { id: 'person-1', displayName: 'Test Person' },
          ],
          marriages: [],
          relationships: [],
        }),
      } as Response)

      await store.selectPersonWithFocus(personOutsideWindow)

      expect(store.focusPersonId).toBe(personOutsideWindow)
      expect(store.selectedPersonId).toBe(personOutsideWindow)
      expect(store.drawerOpen).toBe(true)
      expect(store.isPersonInGraph(personOutsideWindow)).toBe(true)
    })
  })
})
