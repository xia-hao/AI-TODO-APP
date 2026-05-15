import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { Todo, TodoFilters } from '@/types'
import { todosApi } from '@/api/todos'
import { useTeamsStore } from '@/stores/teams'
import { useProjectsStore } from '@/stores/projects'
import i18n from '@/i18n'

export const useTodosStore = defineStore('todos', () => {
  const items = ref<Todo[]>([])
  const filters = ref<TodoFilters>({
    status: '',
    category: '',
    q: '',
    tagIds: [],
    dateFrom: '',
    dateTo: ''
   })
  const teamsStore = useTeamsStore();
  const projectsStore = useProjectsStore();
  const currentProjectId = ref<number | undefined>(undefined)
  const loading = ref(false)
  const currentTeamId = ref<number | undefined>(undefined)
   const selectedIds = ref<Set<number>>(new Set())

  const filteredTodos = computed(() => {
    return items.value.filter(t => {
      if (filters.value.status === 'active' && t.completed) return false
      if (filters.value.status === 'completed' && !t.completed) return false
      if (filters.value.category !== '' && t.category !== filters.value.category) return false
      if (filters.value.q && !t.text.toLowerCase().includes(filters.value.q.toLowerCase())) return false
      // 标签筛选（本地过滤，因为已全量加载）
      if (filters.value.tagIds.length > 0) {
        // 由于 Todo 可能没有携带 tags，这里需要提前加载，暂时跳过（依赖后端过滤）
      }
      if (filters.value.dateFrom && t.dueDate && t.dueDate < filters.value.dateFrom) return false
      if (filters.value.dateTo && t.dueDate && t.dueDate > filters.value.dateTo) return false
      return true
    })
  })

  watch(
    () => [filters.value.status, filters.value.category, filters.value.q, filters.value.tagIds, filters.value.dateFrom, filters.value.dateTo],
    () => {
      if (currentProjectId.value) {
        fetchByProject(currentProjectId.value)
      } else if (currentTeamId.value) {
        fetchTodos(currentTeamId.value)
      } else {
        fetchTodos()
      }
    },
    { deep: true }
  )

  function toggleSelect(todoId: number) {
    const newSet = new Set(selectedIds.value)
    if (newSet.has(todoId)) {
      newSet.delete(todoId)
    } else {
      newSet.add(todoId)
    }
    selectedIds.value = newSet
  }

  function clearSelection() {
    selectedIds.value = new Set()
  }

  async function fetchTodos(teamId?: number) {
    loading.value = true
    currentTeamId.value = teamId
    try {
      const { data } = await todosApi.list({ teamId })
      items.value = data.data
    } finally {
      loading.value = false
    }
  }

  async function createTodo(payload: { text: string; category: string; priority: string; dueDate?: string; projectId?: number; sectionId?: number; teamId?: number; assigneeId?: number | null }) {
    const { data } = await todosApi.create(payload)
    items.value.push(data.data)
    return data.data
  }

  async function updateTodo(id: number, payload: { text: string; category: string; priority: string; dueDate?: string; projectId?: number | null; sectionId?: number | null; teamId?: number | null; assigneeId?: number | null }) {
    const { data } = await todosApi.update(id, payload)
    const idx = items.value.findIndex(t => t.id === id)
    if (idx !== -1) items.value[idx] = data.data
  }

  async function deleteTodo(id: number) {
    await todosApi.delete(id)
    items.value = items.value.filter(t => t.id !== id)
  }

  async function toggleComplete(id: number) {
    const { data } = await todosApi.toggleComplete(id)
    const idx = items.value.findIndex(t => t.id === id)
    if (idx !== -1) items.value[idx] = data.data
  }

  async function reorder(newOrder: Todo[]) {
    items.value = newOrder
    const payload = newOrder.map((t, i) => ({ id: t.id, sortOrder: i }))
    await todosApi.reorder(payload)
  }

  async function exportExcel() {
    const { utils, writeFile } = await import('xlsx')
    const priorityMap: Record<string, string> = { high: i18n.global.t('todo.high'), medium: i18n.global.t('todo.medium'), low: i18n.global.t('todo.low') }
    const sheetName = currentProjectId.value
      ? (projectsStore.currentProject?.name ?? i18n.global.t('project.title'))
      : currentTeamId.value
      ? (teamsStore.currentTeam?.name ?? i18n.global.t('team.title'))
      : i18n.global.t('todo.all')
    const timestamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)

    // 获取当前筛选后的数据用于导出
    let exportData = items.value
    // ... 应用当前筛选器逻辑
    const todos = exportData.filter(t => {
      if (filters.value.status === 'active' && t.completed) return false
      if (filters.value.status === 'completed' && !t.completed) return false
      if (filters.value.category !== '' && t.category !== filters.value.category) return false
      if (filters.value.q && !t.text.toLowerCase().includes(filters.value.q.toLowerCase())) return false
      if (filters.value.dateFrom && t.dueDate && t.dueDate < filters.value.dateFrom) return false
      if (filters.value.dateTo && t.dueDate && t.dueDate > filters.value.dateTo) return false
      return true
    })

    const rows = todos.map((t, i) => ({
      [i18n.global.t('todo.exportTitle')]: i + 1,
      [i18n.global.t('todo.text')]: t.text,
      '状态': t.completed ? i18n.global.t('todo.statusDone') : i18n.global.t('todo.statusUndone'),
      [i18n.global.t('todo.priorityLabel')]: priorityMap[t.priority] ?? t.priority,
      [i18n.global.t('todo.category')]: t.category || '',
      [i18n.global.t('todo.dueDate')]: t.dueDate || '',
      [i18n.global.t('todo.exportCreateTime')]: t.createTime ? t.createTime.slice(0, 10) : '',
      [i18n.global.t('todo.exportUpdateTime')]: t.updateTime ? t.updateTime.slice(0, 10) : ''
    }))
    const ws = utils.json_to_sheet(rows)
    const wb = utils.book_new()
    utils.book_append_sheet(wb, ws, i18n.global.t('todo.exportSheetName'))
    writeFile(wb, `${timestamp}_${sheetName}.xlsx`)
  }

  async function fetchByProject(projectId: number) {
    loading.value = true
    currentTeamId.value = undefined
    try {
      const { data } = await todosApi.listByProject({
        projectId,
        status: filters.value.status,
        category: filters.value.category,
        q: filters.value.q,
        tagIds: filters.value.tagIds.length > 0 ? filters.value.tagIds : undefined,
        dateFrom: filters.value.dateFrom || undefined,
        dateTo: filters.value.dateTo || undefined
      })
      currentProjectId.value = projectId
      items.value = data.data
    } finally {
      loading.value = false
    }
  }

  async function moveSection(todoId: number, sectionId: number) {
    const idx = items.value.findIndex(t => t.id === todoId)
    if (idx === -1) return
    const prev = items.value[idx]
    // 乐观更新：立即更新本地状态，让 Vue 与 SortableJS DOM 一致
    items.value[idx] = { ...prev, sectionId }
    try {
      const { data } = await todosApi.moveSection(todoId, sectionId)
      items.value[idx] = data.data
    } catch (err) {
      // API 失败时回滚
      items.value[idx] = prev
      throw err
    }
  }

  return { items, filters, loading, filteredTodos, currentProjectId, fetchTodos, createTodo, updateTodo, deleteTodo, toggleComplete, reorder, exportExcel,fetchByProject,moveSection,selectedIds,toggleSelect,clearSelection
 }
})
