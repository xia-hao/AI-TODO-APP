import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Project } from '@/types'
import { projectsApi } from '@/api/projects'

export const useProjectsStore = defineStore('projects', () => {
  const projects = ref<Project[]>([])
  const currentProject = ref<Project | null>(null)

  async function fetchProjects() {
    const { data } = await projectsApi.list()
    projects.value = data.data
  }

  async function fetchDetail(id: number) {
    const { data } = await projectsApi.detail(id)
    currentProject.value = data.data
  }

  async function createProject(payload: { name: string; description?: string; color?: string; icon?: string; teamIds?: number[] }) {
    const { data } = await projectsApi.create(payload)
    projects.value.push(data.data)
    return data.data
  }

  async function updateProject(id: number, payload: { name: string; description?: string; color?: string; icon?: string; teamIds?: number[] }) {
    const { data } = await projectsApi.update(id, payload)
    const idx = projects.value.findIndex(p => p.id === id)
    if (idx !== -1) projects.value[idx] = { ...projects.value[idx], ...data.data }
    if (currentProject.value?.id === id) currentProject.value = data.data
  }

  async function deleteProject(id: number) {
    await projectsApi.delete(id)
    projects.value = projects.value.filter(p => p.id !== id)
    if (currentProject.value?.id === id) currentProject.value = null
  }

  return { projects, currentProject, fetchProjects, fetchDetail, createProject, updateProject, deleteProject }
})