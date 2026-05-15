import http from './http'
import type { Comment } from '@/types'

export const commentsApi = {
  list: (todoId: number) =>
    http.get<{ data: Comment[] }>(`/todos/${todoId}/comments`),

  create: (todoId: number, content: string, parentId?: number) =>
    http.post<{ data: Comment }>(`/todos/${todoId}/comments`, { content, parentId }),

  delete: (todoId: number, commentId: number) =>
    http.delete(`/todos/${todoId}/comments/${commentId}`)
}