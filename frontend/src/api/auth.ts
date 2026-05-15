import http from './http'
import type { User } from '@/types'

export const authApi = {
  register: (data: { username: string; email: string; password: string; displayName?: string }) =>
    http.post<{ data: { accessToken: string; user: User } }>('/auth/register', data),

  login: (data: { email: string; password: string }) =>
    http.post<{ data: { accessToken: string; user: User } }>('/auth/login', data),

  refresh: () =>
    http.post<{ data: { accessToken: string; user: User } }>('/auth/refresh', {}, { withCredentials: true }),

  me: () => http.get<{ data: User }>('/auth/me')
}
