import http from './http'
import type { Project } from '@/types'

export const projectsApi = {
  list: () => http.get<{ data: Project[] }>('/projects'),

  detail: (id: number) => http.get<{ data: Project }>(`/projects/${id}`),

  create: (data: { name: string; description?: string; color?: string; icon?: string; teamIds?: number[] }) =>
    http.post<{ data: Project }>('/projects', data),

  update: (id: number, data: { name: string; description?: string; color?: string; icon?: string; teamIds?: number[] }) =>
    http.put<{ data: Project }>(`/projects/${id}`, data),

  delete: (id: number) => http.delete(`/projects/${id}`),

  listByTeam: (teamId: number) =>
    http.get<{ data: Project[] }>('/projects/by-team', { params: { teamId } }),

  getProjectTeams: (projectId: number) =>
    http.get<{ data: import('@/types').TeamBrief[] }>(`/projects/${projectId}/teams`),

  addTeam: (projectId: number, teamId: number) =>
    http.post(`/projects/${projectId}/teams/${teamId}`),

  removeTeam: (projectId: number, teamId: number) =>
    http.delete(`/projects/${projectId}/teams/${teamId}`),
}