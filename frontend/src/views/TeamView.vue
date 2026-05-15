<template>
  <div class="team-view">
    <div v-if="team" class="sticky-top">
      <div class="team-header">
        <h2>{{ team.name }}</h2>
        <p v-if="team.description" class="desc">{{ team.description }}</p>
        <div class="team-actions">
          <el-button size="small" @click="showMembers = true" :icon="User">
            {{ $t('team.members') }}（{{ team.members?.length }}）
          </el-button>
          <el-button v-if="isOwner" size="small" @click="copyCode" :icon="CopyDocument">
            {{ $t('team.inviteCodeLabel') }}{{ team.inviteCode }}
          </el-button>
          <el-button v-if="isOwner" size="small" @click="refreshCode" :icon="Refresh">
            {{ $t('team.refreshCode') }}
          </el-button>
          <el-button v-if="isOwner" size="small" type="danger" @click="confirmDelete">
            {{ $t('team.disband') }}
          </el-button>
        </div>

        <!-- 团队项目列表 -->
        <div class="team-projects-section">
          <div class="section-header">
            <span>{{ $t('team.teamProjects') }}</span>
            <el-button v-if="isOwner || isAdmin" size="small" @click="showCreateProject = true">{{ $t('team.createProject') }}</el-button>
          </div>
          <div class="projects-list">
            <router-link
              v-for="proj in teamProjects"
              :key="proj.id"
              :to="`/projects/${proj.id}`"
              class="project-link"
            >
              <span class="project-dot" :style="{ background: proj.color }" />
              {{ proj.name }}
            </router-link>
            <span v-if="teamProjects.length === 0" class="no-data">{{ $t('team.noProjects') }}</span>
          </div>
        </div>

        <!-- 团队标签管理 -->
        <div class="team-tags-section">
          <div class="section-header">
            <span>{{ $t('team.teamTags') }}</span>
            <el-button v-if="isOwner || isAdmin" size="small" @click="showAddTag = true">{{ $t('team.addTag') }}</el-button>
          </div>
          <!-- 团队标签展示 -->
          <div class="tags-container">
            <div v-if="teamTags.length > 0" class="tags-list">
              <el-tag
                v-for="tag in teamTags"
                :key="tag.id"
                :style="{ backgroundColor: tag.color, color: '#fff', border: 'none' }"
                :closable="isOwner || isAdmin"
                @close="handleDeleteTag(tag.id)"
                size="default"
                effect="dark"
                style="margin: 4px"
              >
                {{ tag.name }}
              </el-tag>
            </div>
            <div
              v-else
              class="tags-empty-state"
              @click="isOwner || isAdmin ? (showAddTag = true) : null"
              :style="{ cursor: (isOwner || isAdmin) ? 'pointer' : 'default' }"
            >
              <el-icon :size="16"><Plus /></el-icon>
              <span>{{ isOwner || isAdmin ? $t('team.noTags') : $t('team.noTagsAdminOnly') }}</span>
            </div>
          </div>
        </div>
      </div>
      <!-- <TodoForm :team-id="teamId" :allow-project-select="true" :filter-team-id="teamId" />
      <TodoFilters /> -->
    </div>
    <el-empty v-else :description="$t('team.notAccessible')" />
    <!-- <TodoList /> -->

    <!-- 成员列表弹窗 -->
    <el-dialog v-model="showMembers" :title="$t('team.members')" width="450px">
      <el-table :data="team?.members" size="small">
        <el-table-column prop="displayName" :label="$t('team.memberName')">
          <template #default="{ row }">
            {{ row.displayName }}
            <span v-if="row.userId === auth.user?.id" style="color:var(--el-text-color-secondary)">{{ $t('app.me') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" :label="$t('team.memberUsername')" />
        <el-table-column prop="role" :label="$t('team.memberRole')" width="80">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.role === 'OWNER' ? 'danger' : row.role === 'ADMIN' ? 'warning' : 'info'"
            >
              {{ { OWNER: $t('team.owner'), ADMIN: $t('team.admin'), MEMBER: $t('team.member') }[row.role as string] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('team.memberAction')" width="140" v-if="isOwner || isAdmin">
          <template #default="{ row }">
            <template v-if="isOwner ? row.role !== 'OWNER' : row.role === 'MEMBER'">
              <el-button
                v-if="isOwner"
                link
                type="primary"
                size="small"
                @click="toggleRole(row)"
              >
                {{ row.role === 'MEMBER' ? $t('team.setAdmin') : $t('team.cancelAdmin') }}
              </el-button>
              <el-button link type="danger" size="small" @click="kickMember(row.userId)">
                {{ $t('team.removeMember') }}
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 添加团队标签对话框 -->
    <el-dialog v-model="showAddTag" :title="$t('team.addTag')" width="350px">
      <el-form :model="newTagForm" @submit.prevent>
        <el-form-item :label="$t('team.tagName')">
          <el-input v-model="newTagForm.name" :placeholder="$t('team.tagName')" />
        </el-form-item>
        <el-form-item :label="$t('team.tagColor')">
          <el-color-picker v-model="newTagForm.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddTag = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="addingTag" @click="handleAddTag">{{ $t('common.add') }}</el-button>
      </template>
    </el-dialog>

    <!-- 创建项目对话框 -->
    <el-dialog v-model="showCreateProject" :title="$t('team.createProject')" width="360px">
      <el-form :model="newProjectForm">
        <el-form-item :label="$t('team.projectName')">
          <el-input v-model="newProjectForm.name" :placeholder="$t('team.projectName')" />
        </el-form-item>
        <el-form-item :label="$t('team.projectDesc')">
          <el-input v-model="newProjectForm.description" :placeholder="$t('team.projectDesc')" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateProject = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creatingProject" @click="handleCreateProject">{{ $t('common.create') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, CopyDocument, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useTeamsStore } from '@/stores/teams'
import { useTodosStore } from '@/stores/todos'
import { useProjectsStore } from '@/stores/projects'
import { tagsApi } from '@/api/tags'
import { projectsApi } from '@/api/projects'
import type { Tag, Project } from '@/types'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const auth = useAuthStore()
const teamsStore = useTeamsStore()
const todos = useTodosStore()
const projectsStore = useProjectsStore()
const teamId = computed(() => Number(route.params.id))
const team = computed(() => teamsStore.currentTeam)
const isOwner = computed(() => team.value?.ownerId === auth.user?.id)
const isAdmin = computed(
  () => team.value?.members?.find(m => m.userId === auth.user?.id)?.role === 'ADMIN'
)
const showMembers = ref(false)

// 团队标签
const teamTags = ref<Tag[]>([])
const showAddTag = ref(false)
const addingTag = ref(false)
const newTagForm = ref({ name: '', color: '#409eff' })

// 团队项目
const teamProjects = ref<Project[]>([])
const showCreateProject = ref(false)
const creatingProject = ref(false)
const newProjectForm = ref({ name: '', description: '' })

async function load() {
  await teamsStore.fetchDetail(teamId.value)
  await todos.fetchTodos(teamId.value)
  await fetchTeamTags()
  await fetchTeamProjects()
}

async function fetchTeamTags() {
  try {
    const { data } = await tagsApi.listForTeam(teamId.value)
    teamTags.value = data.data
  } catch (e) {
    // ignore
  }
}

async function handleAddTag() {
  if (!newTagForm.value.name.trim()) return
  addingTag.value = true
  try {
    await tagsApi.createTeam(teamId.value, newTagForm.value.name.trim(), newTagForm.value.color)
    ElMessage.success(t('team.tagAdded'))
    showAddTag.value = false
    newTagForm.value = { name: '', color: '#409eff' }
    await fetchTeamTags()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('team.tagAddFailed'))
  } finally {
    addingTag.value = false
  }
}

async function handleDeleteTag(tagId: number) {
  try {
    await tagsApi.delete(tagId)
    ElMessage.success(t('team.tagDeleted'))
    await fetchTeamTags()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('team.tagDeleteFailed'))
  }
}

async function fetchTeamProjects() {
  try {
    const { data } = await projectsApi.listByTeam(teamId.value)
    teamProjects.value = data.data
  } catch (e) {
    // ignore
  }
}

async function handleCreateProject() {
  if (!newProjectForm.value.name.trim()) return
  creatingProject.value = true
  try {
    const res = await projectsApi.create({
      name: newProjectForm.value.name.trim(),
      description: newProjectForm.value.description,
      teamIds: [teamId.value]
    })
    ElMessage.success(t('team.projectCreated'))
    showCreateProject.value = false
    newProjectForm.value = { name: '', description: '' }
    // 刷新侧边栏项目列表
    await projectsStore.fetchProjects()
    // 跳转到新建的项目看板
    router.push(`/projects/${res.data.data.id}`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('team.projectCreateFailed'))
  } finally {
    creatingProject.value = false
  }
}

onMounted(load)
watch(teamId, load)

async function copyCode() {
  await navigator.clipboard.writeText(team.value!.inviteCode)
  ElMessage.success(t('team.codeCopied'))
}

async function refreshCode() {
  try {
    const code = await teamsStore.regenerateCode(teamId.value)
    ElMessage.success(t('team.codeRefreshed', { code }))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('team.codeRefreshFailed'))
  }
}

async function kickMember(userId: number) {
  try {
    await ElMessageBox.confirm(t('team.removeConfirm'), t('app.hint'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await teamsStore.removeMember(teamId.value, userId)
    await teamsStore.fetchDetail(teamId.value)
    ElMessage.success(t('team.removed'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('team.removeFailed'))
  }
}

async function toggleRole(member: { userId: number; role: string }) {
  const newRole = member.role === 'MEMBER' ? 'ADMIN' : 'MEMBER'
  try {
    await teamsStore.updateMemberRole(teamId.value, member.userId, newRole)
    ElMessage.success(newRole === 'ADMIN' ? t('team.setAdminDone') : t('team.cancelAdminDone'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('team.roleFailed'))
  }
}

async function confirmDelete() {
  try {
    await ElMessageBox.confirm(t('team.disbandConfirm'), t('team.disband'), { type: 'error' })
  } catch {
    return
  }
  try {
    await teamsStore.deleteTeam(teamId.value)
    router.push('/')
    ElMessage.success(t('team.disbanded'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('team.disbandFailed'))
  }
}
</script>

<style scoped>
.team-view {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 16px;
}
.sticky-top {
  position: sticky;
  top: -24px;
  background: var(--el-bg-color-page);
  z-index: 10;
  padding-top: 24px;
  padding-bottom: 8px;
  margin: -24px -16px 0;
  padding-left: 16px;
  padding-right: 16px;
}
.team-header {
  margin-bottom: 20px;
}
.team-header h2 {
  margin-bottom: 4px;
}
.desc {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  margin-bottom: 12px;
}
.team-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.team-projects-section {
  margin-top: 20px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-light);
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}
.projects-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.project-link {
  display: flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  text-decoration: none;
  font-size: 13px;
}
.project-link:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.project-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}
.no-data {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
.tags-container {
  min-height: 36px;
  display: flex;
  align-items: center;
}
.tags-empty-state {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  transition: border-color 0.2s, color 0.2s;
}
.tags-empty-state:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}
</style>