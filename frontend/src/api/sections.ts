import http from './http'
import type { Section } from '@/types'

export const sectionsApi = {
  list: (projectId: number) =>
    http.get<{ data: Section[] }>(`/projects/${projectId}/sections`),

  create: (projectId: number, name: string) =>
    http.post<{ data: Section }>(`/projects/${projectId}/sections`, { name }),

  update: (projectId: number, sectionId: number, name: string) =>
    http.put<{ data: Section }>(`/projects/${projectId}/sections/${sectionId}`, { name }),

  delete: (projectId: number, sectionId: number) =>
    http.delete(`/projects/${projectId}/sections/${sectionId}`),

  reorder: (projectId: number, orderedIds: number[]) =>
    http.put(`/projects/${projectId}/sections/reorder`, orderedIds)
}