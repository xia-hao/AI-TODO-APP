import http from './http'
import type { Todo, TodoFilters } from '@/types'

export const todosApi = {
  list: (params: Partial<TodoFilters> & { teamId?: number }) =>
    http.get<{ data: Todo[] }>('/todos', { params }),

  create: (data: { text: string; category: string; priority: string; dueDate?: string; teamId?: number; assigneeId?: number | null }) =>
    http.post<{ data: Todo }>('/todos', data),

  update: (id: number, data: { text: string; category: string; priority: string; dueDate?: string; assigneeId?: number | null }) =>
    http.put<{ data: Todo }>(`/todos/${id}`, data),

  delete: (id: number) => http.delete(`/todos/${id}`),

  toggleComplete: (id: number) => http.patch<{ data: Todo }>(`/todos/${id}/complete`),

  reorder: (items: Array<{ id: number; sortOrder: number }>) =>
    http.put('/todos/reorder', items),

  export: (params: Partial<TodoFilters> & { teamId?: number }) =>
    http.get<{ data: Todo[] }>('/todos/export', { params }),

  listByProject: (params: { 
    projectId: number; 
    sectionId?: number;
    tagIds?: number[];
    dateFrom?: string;
    dateTo?: string;
  } & Partial<TodoFilters>) =>
    http.get<{ data: Todo[] }>('/todos/by-project', { 
      params: { ...params, tagIds: params.tagIds?.join(',') }
    }),

  moveSection: (id: number, sectionId: number) =>
    http.patch<{ data: Todo }>(`/todos/${id}/move-section`, { sectionId }),

  search: (q: string) => http.get<{ data: SearchResult[] }>('/todos/search', { params: { q } }),

  import: (projectId: number, items: any[]) =>
    http.post<{ data: Todo[] }>(`/todos/import/${projectId}`, items)
}

export interface SearchResult {
  id: number
  text: string
  projectId: number | null
  projectName: string | null
  completed: boolean
  dueDate: string | null
}
