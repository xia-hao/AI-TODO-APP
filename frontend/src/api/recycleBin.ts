import http from './http'
import type { Todo } from '@/types'

export const recycleBinApi = {
  listDeleted: () => http.get<{ data: Todo[] }>('/todos/deleted'),
  restore: (id: number) => http.patch<{ data: Todo }>(`/todos/${id}/restore`),
  permanentlyDelete: (id: number) => http.delete(`/todos/${id}/permanent`)
}
