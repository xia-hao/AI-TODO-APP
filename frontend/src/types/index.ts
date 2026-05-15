export interface User {
  id: number
  username: string
  email: string
  displayName: string
}

export interface Todo {
  id: number
  text: string
  completed: boolean
  category: string
  priority: 'high' | 'medium' | 'low'
  dueDate: string | null
  sortOrder: number
  ownerId: number
  teamId: number | null
  assigneeId: number | null
  projectId: number | null
  sectionId: number | null
  assigneeName: string | null
  projectName?: string
  teamName?: string
  createBy: number | null
  updateBy: number | null
  createTime: string
  updateTime: string
  deletedTime: string | null
}

export interface Team {
  id: number
  name: string
  description: string
  inviteCode: string
  ownerId: number
  createBy: number | null
  updateBy: number | null
  createTime: string
  updateTime: string
  members?: TeamMember[]
  myRole?: 'OWNER' | 'ADMIN' | 'MEMBER'
}

export interface TeamMember {
  userId: number
  username: string
  displayName: string
  role: 'OWNER' | 'ADMIN' | 'MEMBER'
  joinedAt: string
}

export interface TodoFilters {
  status: '' | 'active' | 'completed'
  category: string
  q: string
  tagIds: number[]
  dateFrom: string
  dateTo: string
}

export interface Project {
  id: number
  name: string
  description: string
  color: string
  icon: string
  ownerId: number
  teamIds: number[]
  teams: TeamBrief[]
  isArchived: boolean
  sortOrder: number
  createBy: number | null
  updateBy: number | null
  createTime: string
  updateTime: string
  sections?: Section[]
}

export interface TeamBrief {
  id: number
  name: string
}

export interface ProjectRequest {
  name: string
  description?: string
  color?: string
  icon?: string
  teamIds?: number[]
}

export interface Section {
  id: number
  projectId: number
  name: string
  sortOrder: number
  createBy: number | null
  updateBy: number | null
  createTime: string
  updateTime: string
}

export interface Subtask {
  id: number
  todoId: number
  text: string
  completed: boolean
  sortOrder: number
  assigneeId: number | null
  assigneeName: string | null
  dueDate: string | null
  createBy: number | null
  updateBy: number | null
  createTime: string
  updateTime: string
}

export interface Tag {
  id: number
  name: string
  color: string
  ownerId: number | null
  teamId: number | null
  projectId: number | null
  createBy: number | null
  updateBy: number | null
  createTime: string
  updateTime: string
}

export interface Comment {
  id: number
  todoId: number
  userId: number
  username: string
  displayName: string
  content: string
  parentId: number | null
  createBy: number | null
  updateBy: number | null
  createTime: string
  updateTime: string
  children?: Comment[]
}

export interface Attachment {
  id: number
  todoId: number
  fileName: string
  fileSize: number
  mimeType: string
  createBy: number | null
  updateBy: number | null
  createTime: string
  updateTime: string
}

export interface Report {
  id: number
  userId: number
  type: 'DAILY' | 'WEEKLY'
  scope: 'SELF' | 'TEAM'
  teamId: number | null
  teamName?: string
  title: string
  preview: string | null
  content: string | null
  jsonData: ReportJsonData | null
  periodStart: string
  periodEnd: string
  createTime: string
}

export interface ReportJsonData {
  summary: {
    totalCompleted: number
    totalCreated: number
    totalOverdue: number
    totalActive: number
    totalMembers?: number
  }
  completedTasks?: Array<{
    id: number
    text: string
    projectName: string
    priority: string
    completedAt: string
    assigneeName?: string
  }>
  createdTasks?: Array<{
    id: number
    text: string
    projectName: string
    priority: string
    createdAt: string
  }>
  overdueTasks?: Array<{
    id: number
    text: string
    projectName: string
    dueDate: string
  }>
  projectStats?: Array<{
    projectId: number
    projectName: string
    total: number
    completed: number
    rate: number
  }>
  memberStats?: Array<{
    userId: number
    displayName: string
    completed: number
    created: number
    overdue: number
    active: number
  }>
}

export interface ActivityLog {
  id: number
  projectId: number | null
  userId: number
  userDisplayName: string
  action: string
  targetType: string
  targetId: number
  detail: string
  createTime: string
}
