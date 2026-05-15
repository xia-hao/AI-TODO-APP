import http from './http'

export const dashboardApi = {
  overview: () => http.get<{ data: any }>('/dashboard/overview'),
  trends: (days = 7) => http.get<{ data: any[] }>('/dashboard/trends', { params: { days } }),
  upcoming: (limit = 5) => http.get<{ data: any[] }>('/dashboard/upcoming', { params: { limit } }),
  projectStats: () => http.get<{ data: any[] }>('/dashboard/projects'),
  assigneeStats: () => http.get<{ data: any[] }>('/dashboard/assignees'),
  tagStats: () => http.get<{ data: any[] }>('/dashboard/tags')
}