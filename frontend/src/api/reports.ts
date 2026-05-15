import http from './http'

export const reportsApi = {
  generate: (type: 'DAILY' | 'WEEKLY') =>
    http.post('/reports/generate', null, { params: { type } }),

  list: (params?: { scope?: string; type?: string; page?: number; size?: number }) =>
    http.get('/reports', { params }),

  get: (id: number) =>
    http.get(`/reports/${id}`),

  delete: (id: number) =>
    http.delete(`/reports/${id}`)
}
