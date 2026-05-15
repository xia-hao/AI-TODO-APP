import http from './http'
import type { Team } from '@/types'

export const teamsApi = {
  list: () => http.get<{ data: Team[] }>('/teams'),

  create: (data: { name: string; description?: string }) =>
    http.post<{ data: Team }>('/teams', data),

  detail: (id: number) => http.get<{ data: Team }>(`/teams/${id}`),

  update: (id: number, data: { name: string; description?: string }) =>
    http.put<{ data: Team }>(`/teams/${id}`, data),

  delete: (id: number) => http.delete(`/teams/${id}`),

  join: (inviteCode: string) =>
    http.post<{ data: Team }>('/teams/join', { inviteCode }),

  removeMember: (teamId: number, userId: number) =>
    http.delete(`/teams/${teamId}/members/${userId}`),

  regenerateCode: (id: number) =>
    http.get<{ data: { inviteCode: string } }>(`/teams/${id}/invite-code`),

  updateRole: (teamId: number, userId: number, role: 'ADMIN' | 'MEMBER') =>
    http.put(`/teams/${teamId}/members/${userId}/role`, { role })
}
