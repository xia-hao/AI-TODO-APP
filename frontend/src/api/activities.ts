import http from './http'
import type { ActivityLog } from '@/types'

export const projectsApi = {
  getActivities: (projectId: number, limit?: number) =>
    http.get<{ data: ActivityLog[] }>(`/projects/${projectId}/activities`, { params: { limit } })
}
