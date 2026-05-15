import http from './http'
import type { Tag } from '@/types'

export const tagsApi = {
  // 获取项目可用标签（项目标签 + 所属团队标签）
  listForProject: (projectId: number) =>
    http.get<{ data: Tag[] }>(`/tags/project/${projectId}`),

  // 创建项目标签
  createProjectTag: (projectId: number, name: string, color?: string) =>
    http.post<{ data: Tag }>(`/tags/project/${projectId}`, { name, color }),

  // 获取团队标签（仅团队标签）
  listForTeam: (teamId: number) =>
    http.get<{ data: Tag[] }>(`/tags/team/${teamId}`),

  // 创建团队标签
  createTeam: (teamId: number, name: string, color?: string) =>
    http.post<{ data: Tag }>('/tags/team', { teamId, name, color }),

  // 删除标签
  delete: (id: number) => http.delete(`/tags/${id}`),

  // 为待办添加标签
  addToTodo: (todoId: number, tagId: number) =>
    http.post(`/tags/todo/${todoId}`, { tagId }),

  // 从待办移除标签
  removeFromTodo: (todoId: number, tagId: number) =>
    http.delete(`/tags/todo/${todoId}/${tagId}`),

  // 获取待办的标签列表
  getForTodo: (todoId: number) =>
    http.get<{ data: Tag[] }>(`/tags/todo/${todoId}`)
}