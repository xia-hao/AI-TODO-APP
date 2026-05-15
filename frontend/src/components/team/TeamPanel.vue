<template>
  <div class="team-panel">
    <div class="panel-header">
      <span>{{ $t('team.title') }}</span>
      <el-button link :icon="Plus" @click="showCreate = true" />
    </div>

    <div
      v-for="team in teams.teams"
      :key="team.id"
      class="team-item-wrapper"
      :class="{ active: currentTeamId === team.id }"
    >
      <div class="team-item-header" @click="router.push(`/team/${team.id}`)">
        <el-icon><UserFilled /></el-icon>
        <span>{{ team.name }}</span>
        <el-tag v-if="team.myRole === 'OWNER'" size="small" type="danger" class="role-tag">{{ $t('team.owner') }}</el-tag>
        <el-tag v-else-if="team.myRole === 'ADMIN'" size="small" type="warning" class="role-tag">{{ $t('team.admin') }}</el-tag>
        <el-tag v-else-if="team.myRole === 'MEMBER'" size="small" type="info" class="role-tag">{{ $t('team.member') }}</el-tag>
        <el-icon class="expand-icon" :class="{ expanded: expandedTeamIds.has(team.id) }" @click.stop="toggleExpand(team.id)">
          <ArrowRight />
        </el-icon>
      </div>
      <div v-if="expandedTeamIds.has(team.id) && teamProjects[team.id]" class="team-projects">
        <div v-if="teamProjects[team.id]!.length === 0" class="no-projects">{{ $t('team.noProjects') }}</div>
        <router-link
          v-for="p in teamProjects[team.id]"
          :key="p.id"
          :to="`/projects/${p.id}`"
          class="team-project-item"
        >
          <span class="project-dot" :style="{ background: p.color || '#409eff' }"></span>
          <span class="project-name">{{ p.name }}</span>
        </router-link>
      </div>
    </div>

    <div class="join-area">
      <el-input v-model="joinCode" :placeholder="$t('team.codePlaceholder')" size="small" clearable>
        <template #append>
          <el-button @click="doJoin" :loading="joining">{{ $t('team.join') }}</el-button>
        </template>
      </el-input>
    </div>

    <!-- 创建团队对话框 -->
    <el-dialog v-model="showCreate" :title="$t('team.create')" width="360px" :z-index="9999">
      <el-form :model="createForm" ref="createFormRef">
        <el-form-item prop="name" :rules="[{ required: true, message: $t('team.namePlaceholder') }]">
          <el-input v-model="createForm.name" :placeholder="$t('team.namePlaceholder')" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="createForm.description" :placeholder="$t('team.descPlaceholder')" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doCreate" :loading="creating">{{ $t('common.create') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, UserFilled, ArrowRight } from '@element-plus/icons-vue'
import { useTeamsStore } from '@/stores/teams'
import { projectsApi } from '@/api/projects'

const teams = useTeamsStore()
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const currentTeamId = computed(() => route.params.id ? Number(route.params.id) : null)

const showCreate = ref(false)
const creating = ref(false)
const createFormRef = ref()
const createForm = ref({ name: '', description: '' })

const joinCode = ref('')
const joining = ref(false)

const teamProjects = ref<Record<number, import('@/types').Project[]>>({})
const expandedTeamIds = ref(new Set<number>())

async function toggleExpand(teamId: number) {
  if (expandedTeamIds.value.has(teamId)) {
    expandedTeamIds.value.delete(teamId)
    return
  }
  expandedTeamIds.value.add(teamId)
  if (!teamProjects.value[teamId]) {
    try {
      const { data } = await projectsApi.listByTeam(teamId)
      teamProjects.value[teamId] = data.data
    } catch {
      teamProjects.value[teamId] = []
    }
  }
}

async function doCreate() {
  await createFormRef.value.validate()
  creating.value = true
  try {
    const team = await teams.createTeam(createForm.value.name, createForm.value.description || undefined)
    showCreate.value = false
    createForm.value = { name: '', description: '' }
    ElMessage.success(t('team.created'))
    router.push(`/team/${team.id}`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('team.createFailed'))
  } finally {
    creating.value = false
  }
}

async function doJoin() {
  if (!joinCode.value.trim()) return
  joining.value = true
  try {
    const team = await teams.joinTeam(joinCode.value.trim())
    joinCode.value = ''
    router.push(`/team/${team.id}`)
    ElMessage.success(t('team.joined', { name: team.name }))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('team.joinFailed'))
  } finally {
    joining.value = false
  }
}
</script>

<style scoped>
.team-panel { padding: 8px 0; }
.panel-header { display: flex; justify-content: space-between; align-items: center; padding: 0 12px 8px; font-weight: 600; color: var(--el-text-color-regular); font-size: 13px; }
.join-area { padding: 8px 12px 0; }
.team-item-wrapper { margin-bottom: 2px; }
.team-item-header { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 6px; cursor: pointer; color: var(--el-text-color-primary); text-decoration: none; font-size: 14px; transition: background 0.15s; }
.team-item-header:hover, .team-item-wrapper.active .team-item-header { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.team-projects { padding-left: 32px; margin-bottom: 4px; }
.team-project-item { display: flex; align-items: center; gap: 6px; padding: 4px 8px; border-radius: 4px; color: var(--el-text-color-regular); text-decoration: none; font-size: 13px; transition: background 0.15s; }
.team-project-item:hover { background: var(--el-fill-color-light); color: var(--el-color-primary); }
.role-tag { margin-left: auto; }
.project-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; flex-shrink: 0; }
.project-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.no-projects { font-size: 12px; color: var(--el-text-color-placeholder); padding: 4px 8px; }
.expand-icon { transition: transform 0.2s; font-size: 12px; }
.expand-icon.expanded { transform: rotate(90deg); }
</style>
