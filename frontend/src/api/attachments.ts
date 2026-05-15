import http from './http'
import type { Attachment } from '@/types'

export const attachmentsApi = {
  list: (todoId: number) =>
    http.get<{ data: Attachment[] }>(`/todos/${todoId}/attachments`),

  upload: (todoId: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<{ data: Attachment }>(`/todos/${todoId}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  delete: (todoId: number, attachmentId: number) =>
    http.delete(`/todos/${todoId}/attachments/${attachmentId}`),

  getDownloadUrl: (todoId: number, attachmentId: number) =>
    `/api/todos/${todoId}/attachments/${attachmentId}/download`
}