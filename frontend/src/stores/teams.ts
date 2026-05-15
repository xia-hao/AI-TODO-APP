import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Team } from '@/types'
import { teamsApi } from '@/api/teams'

export const useTeamsStore = defineStore('teams', () => {
  const teams = ref<Team[]>([])
  const currentTeam = ref<Team | null>(null)

  async function fetchTeams() {
    const { data } = await teamsApi.list()
    teams.value = data.data
  }

  async function createTeam(name: string, description?: string) {
    const { data } = await teamsApi.create({ name, description })
    teams.value.push(data.data)
    return data.data
  }

  async function fetchDetail(id: number) {
    const { data } = await teamsApi.detail(id)
    currentTeam.value = data.data
    return data.data
  }

  async function joinTeam(inviteCode: string) {
    const { data } = await teamsApi.join(inviteCode)
    teams.value.push(data.data)
    return data.data
  }

  async function deleteTeam(id: number) {
    await teamsApi.delete(id)
    teams.value = teams.value.filter(t => t.id !== id)
    if (currentTeam.value?.id === id) currentTeam.value = null
  }

  async function removeMember(teamId: number, userId: number) {
    await teamsApi.removeMember(teamId, userId)
    if (currentTeam.value?.id === teamId) {
      currentTeam.value.members = currentTeam.value.members?.filter(m => m.userId !== userId)
    }
  }

  async function regenerateCode(id: number) {
    const { data } = await teamsApi.regenerateCode(id)
    const team = teams.value.find(t => t.id === id)
    if (team) team.inviteCode = data.data.inviteCode
    if (currentTeam.value?.id === id) currentTeam.value.inviteCode = data.data.inviteCode
    return data.data.inviteCode
  }

  async function updateMemberRole(teamId: number, userId: number, role: 'ADMIN' | 'MEMBER') {
    await teamsApi.updateRole(teamId, userId, role)
    if (currentTeam.value?.id === teamId && currentTeam.value.members) {
      const member = currentTeam.value.members.find(m => m.userId === userId)
      if (member) member.role = role
    }
  }

  return { teams, currentTeam, fetchTeams, createTeam, fetchDetail, joinTeam, deleteTeam, removeMember, regenerateCode, updateMemberRole }
})
