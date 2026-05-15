import http from './http'
import type { Subtask } from '@/types'

export const subtasksApi = {
  list: (todoId: number) =>
    http.get<{ data: Subtask[] }>(`/todos/${todoId}/subtasks`),

  create: (todoId: number, data: { text: string; assigneeId?: number | null; dueDate?: string }) =>
    http.post<{ data: Subtask }>(`/todos/${todoId}/subtasks`, data),

  update: (todoId: number, subtaskId: number, data: { text: string; assigneeId?: number | null; dueDate?: string }) =>
    http.put<{ data: Subtask }>(`/todos/${todoId}/subtasks/${subtaskId}`, data),

  toggleComplete: (todoId: number, subtaskId: number) =>
    http.patch<{ data: Subtask }>(`/todos/${todoId}/subtasks/${subtaskId}/complete`),

  delete: (todoId: number, subtaskId: number) =>
    http.delete(`/todos/${todoId}/subtasks/${subtaskId}`),

  reorder: (todoId: number, items: Array<{ id: number; sortOrder: number }>) =>
    http.put(`/todos/${todoId}/subtasks/reorder`, items)
}