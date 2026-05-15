import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useTodosStore } from './todos'

describe('todos store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should initialize with empty items', () => {
    const store = useTodosStore()
    expect(store.items).toEqual([])
    expect(store.loading).toBe(false)
  })

  it('should toggle selection', () => {
    const store = useTodosStore()
    store.toggleSelect(1)
    expect(store.selectedIds.has(1)).toBe(true)
    store.toggleSelect(1)
    expect(store.selectedIds.has(1)).toBe(false)
  })

  it('should clear selection', () => {
    const store = useTodosStore()
    store.toggleSelect(1)
    store.toggleSelect(2)
    store.clearSelection()
    expect(store.selectedIds.size).toBe(0)
  })
})
