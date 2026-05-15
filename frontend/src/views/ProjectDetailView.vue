<template>
  <div class="project-detail-view">
    <!-- 固定头部 -->
    <div v-if="project" class="sticky-top">
      <div class="project-header">
        <div class="header-top">
          <h2>
            <span class="project-color-dot" :style="{ background: project.color }" />
            {{ project.name }}
          </h2>
          <div class="header-actions">
            <el-button size="small" @click="showImport = true">
              <el-icon><UploadFilled /></el-icon> {{ $t('project.import') }}
            </el-button>
            <el-button v-if="isOwner" size="small" type="danger" :icon="Delete" @click="confirmDeleteProject">
              {{ $t('project.delete') }}
            </el-button>
          </div>
        </div>
        <p class="desc">{{ project.description }}</p>
        <div class="project-teams">
          <template v-for="t in projectTeams" :key="t.id">
            <div class="project-team-chip">
              <router-link :to="`/team/${t.id}`" class="chip-link">{{ t.name }}</router-link>
              <div class="chip-actions">
                <el-icon class="chip-action" :title="$t('project.viewMembers')" @click="showTeamMembers(t.id)"><UserFilled /></el-icon>
                <el-icon v-if="isOwner" class="chip-action chip-remove" :title="$t('project.unlinkTeam')" @click="confirmRemoveTeam(t.id)"><Close /></el-icon>
              </div>
            </div>
          </template>
          <div v-if="isOwner" class="project-team-chip add-chip" @click="openTeamMgmt">
            <el-icon><Plus /></el-icon>
            <span>{{ $t('project.associatedTeams') }}</span>
          </div>
          <span v-if="projectTeams.length === 0 && !isOwner" class="no-team">{{ $t('project.noAssociatedTeam') }}</span>
        </div>
      </div>

      <!-- 批量操作栏 -->
      <div v-if="selectedIds.size > 0" class="batch-bar">
        <span>{{ $t('todo.selectedCount', { n: selectedIds.size }) }}</span>
        <el-button size="small" @click="batchComplete">{{ $t('todo.batchComplete') }}</el-button>
        <el-button size="small" type="danger" @click="batchDelete">{{ $t('todo.batchDelete') }}</el-button>
        <el-select v-model="batchMoveSection" size="small" :placeholder="$t('todo.batchMove')" clearable @change="batchMove">
          <el-option v-for="s in project.sections" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button size="small" @click="todos.clearSelection()">{{ $t('todo.clearSelection') }}</el-button>
      </div>

      <!-- 项目标签管理 -->
      <div class="project-tags-section">
        <div class="section-header">
          <span>{{ $t('project.tags') }}</span>
          <el-button size="small" @click="showAddTag = true">{{ $t('project.addTag') }}</el-button>
        </div>
        <div class="tags-container">
          <div v-if="projectTags.length > 0" class="tags-list">
            <el-tag
              v-for="tag in projectTags"
              :key="tag.id"
              :style="{ backgroundColor: tag.color, color: '#fff', border: 'none' }"
              closable
              @close="handleDeleteTag(tag.id)"
              size="default"
              effect="dark"
              style="margin: 4px"
            >
              {{ tag.name }}
            </el-tag>
          </div>
          <div v-else class="tags-empty-state" @click="showAddTag = true">
            <el-icon :size="18"><Plus /></el-icon>
            <span>{{ $t('project.noTags') }}</span>
          </div>
        </div>
      </div>

      <TodoForm :project-id="projectId" />
      <TodoFilters />
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="4" animated />
    </div>

    <!-- 看板主体 -->
    <div v-else class="sections-board" ref="boardRef">
      <div
        v-for="section in projectSections"
        :key="section.id"
        :data-section-id="section.id"
        class="section-column"
      >
        <div class="section-header">
          <span>{{ section.name }}</span>
          <div class="section-actions">
            <el-button link :icon="Edit" size="small" @click="startRename(section)" />
            <el-button link :icon="Delete" size="small" @click="confirmDeleteSection(section)" />
            <span class="section-count">{{ sectionTodos(section.id).length }}</span>
          </div>
        </div>

        <div class="section-todos" :data-section-id="section.id">
          <TodoItem
            v-for="todo in sectionTodos(section.id)"
            :key="todo.id"
            :todo="todo"
            :selectable="true"
            :selected="selectedIds.has(todo.id)"
            @update:selected="() => todos.toggleSelect(todo.id)"
            class="board-todo-item"
          />
        </div>
      </div>

      <!-- 添加分区按钮 -->
      <div class="add-section-column" @click="showAddSection = true">
        <el-icon :size="20"><Plus /></el-icon>
        <span>{{ $t('project.addSection') }}</span>
      </div>
    </div>

    <!-- 对话框：重命名分区 -->
    <el-dialog v-model="renameVisible" :title="$t('project.renameSection')" width="300px">
      <el-input v-model="renameForm.name" :placeholder="$t('project.newName')" />
      <template #footer>
        <el-button @click="renameVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doRename">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 对话框：添加分区 -->
    <el-dialog v-model="showAddSection" :title="$t('project.addSection')" width="300px" @closed="addSectionName = ''">
      <el-input v-model="addSectionName" :placeholder="$t('project.sectionName')" />
      <template #footer>
        <el-button @click="showAddSection = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doAddSection">{{ $t('common.add') }}</el-button>
      </template>
    </el-dialog>

    <!-- 对话框：添加项目标签 -->
    <el-dialog v-model="showAddTag" :title="$t('project.addTag')" width="300px">
      <el-form :model="newTagForm">
        <el-form-item :label="$t('tag.name')">
          <el-input v-model="newTagForm.name" :placeholder="$t('project.tagName')" />
        </el-form-item>
        <el-form-item :label="$t('project.tagColor')">
          <el-color-picker v-model="newTagForm.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddTag = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="addingTag" @click="handleAddTag">{{ $t('common.add') }}</el-button>
      </template>
    </el-dialog>
    <!-- 导入对话框 -->
    <ImportDialog v-model="showImport" @imported="refreshData" />

    <!-- 管理团队对话框 -->
    <el-dialog v-model="showTeamMgmt" :title="$t('project.associatedTeams')" width="360px">
      <el-form>
        <el-form-item :label="$t('project.selectTeam')">
          <el-select v-model="newTeamIds" multiple :placeholder="$t('project.selectTeamPlaceholder')" style="width:100%">
            <el-option v-for="t in availableTeams" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTeamMgmt = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveTeamAssociations" :loading="savingTeams">{{ $t('project.confirmLink') }}</el-button>
      </template>
    </el-dialog>

    <!-- 团队成员对话框 -->
    <el-dialog v-model="showMembers" :title="selectedTeamName + ' - ' + $t('team.members')" width="420px">
      <el-table :data="selectedTeamMembers" size="small">
        <el-table-column prop="displayName" :label="$t('team.memberName')" />
        <el-table-column prop="role" :label="$t('team.memberRole')" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.role === 'OWNER' ? 'danger' : row.role === 'ADMIN' ? 'warning' : 'info'">
              {{ { OWNER: $t('team.owner'), ADMIN: $t('team.admin'), MEMBER: $t('team.member') }[row.role as string] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="selectedTeamInviteCode" class="invite-section">
        <span class="invite-label">{{ $t('team.inviteCodeLabel') }}<strong>{{ selectedTeamInviteCode }}</strong></span>
        <el-button size="small" :icon="CopyDocument" @click="copyInviteCode">{{ $t('common.copy') }}</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Sortable from 'sortablejs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, UploadFilled, Close, CopyDocument, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useProjectsStore } from '@/stores/projects'
import { useTodosStore } from '@/stores/todos'
import { useTeamsStore } from '@/stores/teams'
import { sectionsApi } from '@/api/sections'
import { tagsApi } from '@/api/tags'
import { teamsApi } from '@/api/teams'
import { todosApi } from '@/api/todos'
import { projectsApi } from '@/api/projects'
import TodoForm from '@/components/todo/TodoForm.vue'
import TodoFilters from '@/components/todo/TodoFilters.vue'
import TodoItem from '@/components/todo/TodoItem.vue'
import ImportDialog from '@/components/import/ImportDialog.vue'
import type { Section, Tag, TeamMember } from '@/types'
import { subscribeToProject } from '@/services/websocket'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const auth = useAuthStore()
const projectsStore = useProjectsStore()
const todos = useTodosStore()
const teamsStore = useTeamsStore()
const projectId = computed(() => Number(route.params.projectId))
const project = computed(() => projectsStore.currentProject)
const loading = computed(() => todos.loading)
const selectedIds = computed(() => todos.selectedIds ?? new Set<number>())
const projectSections = computed(() => project.value?.sections ?? [])
const isOwner = computed(() => project.value?.ownerId === auth.user?.id)

// 团队关联管理
const projectTeams = computed(() => project.value?.teams ?? [])
const availableTeams = computed(() =>
  teamsStore.teams.filter(t => !projectTeams.value.some(pt => pt.id === t.id))
)
const showTeamMgmt = ref(false)
const savingTeams = ref(false)
const newTeamIds = ref<number[]>([])

const showMembers = ref(false)
const selectedTeamMembers = ref<TeamMember[]>([])
const selectedTeamName = ref('')
const selectedTeamInviteCode = ref('')

function openTeamMgmt() {
  newTeamIds.value = []
  showTeamMgmt.value = true
}

async function saveTeamAssociations() {
  if (newTeamIds.value.length === 0) return
  savingTeams.value = true
  try {
    await Promise.all(newTeamIds.value.map(tid => projectsApi.addTeam(projectId.value, tid)))
    ElMessage.success(t('project.teamLinked'))
    showTeamMgmt.value = false
    await projectsStore.fetchDetail(projectId.value)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('project.teamLinkFailed'))
  } finally {
    savingTeams.value = false
  }
}

async function confirmRemoveTeam(teamId: number) {
  try {
    await ElMessageBox.confirm(t('project.teamUnlinkConfirm'), t('app.hint'), { type: 'warning' })
  } catch { return }
  try {
    await projectsApi.removeTeam(projectId.value, teamId)
    ElMessage.success(t('project.teamUnlinked'))
    await projectsStore.fetchDetail(projectId.value)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('project.teamUnlinkFailed'))
  }
}

async function showTeamMembers(teamId: number) {
  try {
    const { data } = await teamsApi.detail(teamId)
    selectedTeamMembers.value = data.data.members ?? []
    const team = projectTeams.value.find(t => t.id === teamId)
    selectedTeamName.value = team?.name ?? ''
    selectedTeamInviteCode.value = data.data.inviteCode ?? ''
    showMembers.value = true
  } catch {
    ElMessage.error(t('project.fetchMembersFailed'))
  }
}

function copyInviteCode() {
  navigator.clipboard.writeText(selectedTeamInviteCode.value)
  ElMessage.success(t('project.inviteCodeCopied'))
}

const boardRef = ref<HTMLElement>()
const batchMoveSection = ref<number | null>(null)
const showImport = ref(false)
const unsubscribeProject = ref<(() => void) | null>(null)

// 标签管理状态
const projectTags = ref<Tag[]>([])
const showAddTag = ref(false)
const addingTag = ref(false)
const newTagForm = ref({ name: '', color: '#409eff' })

// 分区管理状态
const renameVisible = ref(false)
const renameForm = ref({ id: 0, name: '' })
const showAddSection = ref(false)
const addSectionName = ref('')

function sectionTodos(sectionId: number) {
  return todos.items
    .filter(t => t != null && t.sectionId === sectionId)
    .sort((a, b) => a.sortOrder - b.sortOrder)
}

// ---------- Sortable 实例管理 ----------
let sortables: Sortable[] = []
let sectionSortable: Sortable | null = null

watch(projectId, () => {
  load()
  setupProjectSubscription()
})

/** 初始化列内待办拖拽 */
function initSortable() {
  if (!boardRef.value) return
  sortables.forEach(s => s.destroy())
  sortables = []

  const columns = boardRef.value.querySelectorAll('.section-todos')
  columns.forEach(col => {
    const instance = Sortable.create(col as HTMLElement, {
      group: 'board',
      animation: 150,
      handle: '.drag-handle',
      ghostClass: 'sortable-ghost',
      forceFallback: true,
      onEnd(event) {
        const { item, from, to, oldIndex, newIndex } = event
        const todoId = Number(item.dataset.todoId)
        const fromSectionId = Number(from.dataset.sectionId)
        const toSectionId = Number(to.dataset.sectionId)
        if (fromSectionId === toSectionId && oldIndex !== undefined && newIndex !== undefined) {
          const items = todos.items.filter(t => t?.sectionId === fromSectionId)
            .sort((a, b) => a.sortOrder - b.sortOrder)
          const [moved] = items.splice(oldIndex, 1)
          items.splice(newIndex, 0, moved)
          items.forEach((t, i) => {
            const idx = todos.items.findIndex(item => item.id === t.id)
            if (idx !== -1) todos.items[idx] = { ...todos.items[idx], sortOrder: i }
          })
          return todosApi.reorder(items.map((t, i) => ({ id: t.id, sortOrder: i })))
        } else if (fromSectionId !== toSectionId) {
          const todoIdx = todos.items.findIndex(t => t.id === todoId)
          if (todoIdx === -1) return
          // 同步更新所有本地状态，避免异步 gap 导致 DOM 闪烁
          todos.items[todoIdx] = { ...todos.items[todoIdx], sectionId: toSectionId }
          // 构建目标分区排序后的列表，将移动项插入正确位置
          const targetItems = todos.items
            .filter(t => t?.sectionId === toSectionId)
            .sort((a, b) => a.sortOrder - b.sortOrder)
          const movedIdx = targetItems.findIndex(t => t.id === todoId)
          if (movedIdx !== -1) {
            const [moved] = targetItems.splice(movedIdx, 1)
            const insertAt = Math.min(newIndex ?? targetItems.length, targetItems.length)
            targetItems.splice(insertAt, 0, moved)
          }
          // 统一更新目标分区所有项的 sortOrder
          targetItems.forEach((t, i) => {
            const idx = todos.items.findIndex(item => item.id === t.id)
            if (idx !== -1) todos.items[idx] = { ...todos.items[idx], sortOrder: i }
          })
          // 异步持久化，不等待、不触发额外渲染
          Promise.all([
            todosApi.moveSection(todoId, toSectionId),
            todosApi.reorder(targetItems.map((t, i) => ({ id: t.id, sortOrder: i })))
          ]).then(([moveRes]) => {
            // 后端自动化规则可能改变了状态（如移入"已完成"分区自动标记完成）
            if (moveRes?.data?.data) {
              const idx = todos.items.findIndex(t => t.id === todoId)
              if (idx !== -1) {
                todos.items[idx] = { ...todos.items[idx], completed: moveRes.data.data.completed }
              }
            }
          }).catch(() => {
            ElMessage.error(t('project.moveFailed'))
            todos.fetchByProject(projectId.value)
          })
        }
      }
    })
    sortables.push(instance)
  })
}

/** 安全重初始化所有 Sortable 实例（nextTick 确保 DOM 已就绪） */
function reinitSortables() {
  nextTick(() => {
    initSortable()
    initSectionSortable()
  })
}

/** 初始化分区列拖拽排序 */
function initSectionSortable() {
  if (!boardRef.value) return
  sectionSortable?.destroy()
  sectionSortable = null

  const columnsContainer = boardRef.value
  sectionSortable = Sortable.create(columnsContainer, {
    animation: 150,
    handle: '.section-header',
    draggable: '.section-column',
    onEnd(event) {
      const { oldIndex, newIndex } = event
      if (oldIndex === undefined || newIndex === undefined || oldIndex === newIndex) return
      const orderedSections = [...projectSections.value]
      const [moved] = orderedSections.splice(oldIndex, 1)
      orderedSections.splice(newIndex, 0, moved)
      const orderedIds = orderedSections.map(s => s.id)
      sectionsApi.reorder(projectId.value, orderedIds).catch((err) => {
        ElMessage.error(t('project.sectionSortFailed'))
        console.error(err)
      })
    }
  })
}

// ---------- 标签管理方法 ----------
async function fetchProjectTags() {
  try {
    const { data } = await tagsApi.listForProject(projectId.value)
    projectTags.value = data.data
  } catch (e) { /* ignore */ }
}

async function handleAddTag() {
  if (!newTagForm.value.name.trim()) return
  addingTag.value = true
  try {
    await tagsApi.createProjectTag(projectId.value, newTagForm.value.name.trim(), newTagForm.value.color)
    ElMessage.success(t('project.tagAdded'))
    showAddTag.value = false
    newTagForm.value = { name: '', color: '#409eff' }
    await fetchProjectTags()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('project.tagAddFailed'))
  } finally {
    addingTag.value = false
  }
}

async function handleDeleteTag(tagId: number) {
  try {
    await tagsApi.delete(tagId)
    ElMessage.success(t('project.tagDeleted'))
    await fetchProjectTags()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('project.tagDeleteFailed'))
  }
}

// ---------- 分区管理方法 ----------
function startRename(section: Section) {
  renameForm.value = { id: section.id, name: section.name }
  renameVisible.value = true
}

async function doRename() {
  if (!renameForm.value.name.trim()) return
  try {
    await sectionsApi.update(projectId.value, renameForm.value.id, renameForm.value.name.trim())
    ElMessage.success(t('project.sectionRenamed'))
    renameVisible.value = false
    await projectsStore.fetchDetail(projectId.value)
    await todos.fetchByProject(projectId.value)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('project.sectionRenameFailed'))
  }
}

function confirmDeleteSection(section: Section) {
  ElMessageBox.confirm(
    t('project.deleteSectionConfirm', { name: section.name }),
    t('common.delete'),
    { type: 'warning' }
  )
    .then(() => doDeleteSection(section.id))
    .catch(() => {})
}

async function doDeleteSection(sectionId: number) {
  try {
    await sectionsApi.delete(projectId.value, sectionId)
    ElMessage.success(t('project.sectionDeleted'))
    await projectsStore.fetchDetail(projectId.value)
    await todos.fetchByProject(projectId.value)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('project.sectionDeleteFailed'))
  }
}

async function doAddSection() {
  if (!addSectionName.value.trim()) return
  try {
    await sectionsApi.create(projectId.value, addSectionName.value.trim())
    ElMessage.success(t('project.sectionAdded'))
    showAddSection.value = false
    addSectionName.value = ''
    await projectsStore.fetchDetail(projectId.value)
    await todos.fetchByProject(projectId.value)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('project.sectionAddFailed'))
  }
}

// ---------- 批量操作 ----------
async function batchComplete() {
  try {
    const ids = [...selectedIds.value]
    await Promise.all(ids.map(id => todos.toggleComplete(id)))
    ElMessage.success(t('project.batchDone'))
    todos.clearSelection()
  } catch { ElMessage.error(t('project.batchFailed')) }
}

async function batchDelete() {
  try {
    await ElMessageBox.confirm(t('project.batchDeleteConfirm', { n: selectedIds.value.size }), t('project.batchDeleteTitle'), { type: 'error' })
  } catch { return }
  try {
    const ids = [...selectedIds.value]
    await Promise.all(ids.map(id => todos.deleteTodo(id)))
    ElMessage.success(t('project.batchDeleted'))
    todos.clearSelection()
  } catch { ElMessage.error(t('project.batchFailed')) }
}

async function batchMove(sectionId: number) {
  if (!sectionId) return
  try {
    const ids = [...selectedIds.value]
    await Promise.all(ids.map(id => todos.moveSection(id, sectionId)))
    ElMessage.success(t('project.batchMoveSuccess'))
    batchMoveSection.value = null
    todos.clearSelection()
  } catch { ElMessage.error(t('project.batchMoveFailed')) }
}

// ---------- 项目删除 ----------
async function confirmDeleteProject() {
  try {
    await ElMessageBox.confirm(
      t('project.deleteProjectConfirm', { name: project.value?.name }),
      t('project.delete'),
      { type: 'error' }
    )
  } catch { return }
  try {
    await projectsStore.deleteProject(projectId.value)
    ElMessage.success(t('project.deleted'))
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('project.deleteFailed'))
  }
}

// ---------- 生命周期 ----------
async function load() {
  if (!projectId.value) return
  await Promise.all([
    projectsStore.fetchDetail(projectId.value),
    todos.fetchByProject(projectId.value),
    fetchProjectTags(),
    teamsStore.teams.length === 0 ? teamsStore.fetchTeams() : Promise.resolve()
  ])
}

function refreshData() {
  load()
}

function setupProjectSubscription() {
  // 取消旧订阅
  if (unsubscribeProject.value) {
    unsubscribeProject.value()
    unsubscribeProject.value = null
  }
  if (!projectId.value) return
  unsubscribeProject.value = subscribeToProject(projectId.value, () => {
    // 静默刷新看板，不打扰用户
    load()
  })
}

onMounted(async () => {
  await load()
  setupProjectSubscription()
})

watch([() => todos.items.length, () => project.value?.sections, () => todos.loading], ([, , newLoading], [, , oldLoading]) => {
  // 仅在 loading 从 true → false 时重初始化（看板刚重建完毕）
  if (oldLoading === true && newLoading === false) {
    reinitSortables()
  }
})

onUnmounted(() => {
  sortables.forEach(s => s.destroy())
  sectionSortable?.destroy()
  if (unsubscribeProject.value) {
    unsubscribeProject.value()
    unsubscribeProject.value = null
  }
})
</script>

<style scoped>
.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.header-top h2 {
  margin-bottom: 0;
}
.header-actions { display: flex; gap: 8px; }
.project-detail-view { max-width: 100%; margin: 0 auto; padding: 24px 16px; }
.sticky-top { position: sticky; top: -24px; background: var(--el-bg-color-page); z-index: 10; padding-top: 24px; padding-bottom: 8px; margin: -24px -16px 0; padding-left: 16px; padding-right: 16px; }
.project-header { margin-bottom: 16px; }
.project-color-dot { display: inline-block; width: 14px; height: 14px; border-radius: 50%; margin-right: 8px; vertical-align: middle; }
.desc { color: var(--el-text-color-secondary); }
.batch-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; padding: 8px 12px; background: var(--el-color-primary-light-9); border-radius: 6px; flex-wrap: wrap; }
.sections-board { display: flex; gap: 16px; overflow-x: auto; padding-bottom: 16px; min-height: 200px;margin-top: 5px; }
.section-column { display: flex; flex-direction: column; flex: 1; min-width: 280px; height: calc(100vh - 520px); overflow-y: auto; background: var(--el-bg-color-page); border-radius: 8px; padding-bottom: 40px; }
.section-column::-webkit-scrollbar { width: 4px; }
.section-column::-webkit-scrollbar-thumb { background: var(--el-text-color-placeholder); border-radius: 2px; }
.section-todos { flex: 1; min-height: 60px; padding: 0 12px; }
.section-todos::before { content: ''; display: block; height: 10px; }
.section-todos::after { content: ''; display: block; height: 10px; }
.section-header { position: sticky; top: 0; z-index: 2; background: var(--el-bg-color-page); display: flex; justify-content: space-between; align-items: center; padding: 12px 12px 8px; border-radius: 8px 8px 0 0; font-weight: 600; color: var(--el-text-color-primary); cursor: grab; }
.section-actions { display: flex; align-items: center; gap: 4px; }
.section-count { background: var(--el-border-color); color: var(--el-text-color-regular); padding: 2px 8px; border-radius: 10px; font-size: 12px; margin-left: 8px; }
.add-section-column { min-width: 200px; height: fit-content; background: var(--el-bg-color); border: 2px dashed var(--el-border-color); border-radius: 8px; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px; cursor: pointer; color: var(--el-text-color-secondary); transition: border-color 0.2s; }
.add-section-column:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
.loading-wrap { padding: 24px; }
:deep(.sortable-ghost) { opacity: 0.4; background: var(--el-text-color-placeholder); }
@media (max-width: 767px) { .sections-board { flex-direction: column; overflow-x: hidden; } .section-column { min-width: 100%; margin-bottom: 12px; } .add-section-column { min-width: 100%; } }
.project-tags-section {
  height: 100px;
}
.project-teams { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
.project-team-chip { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; background: var(--el-color-primary-light-9); border-radius: 4px; font-size: 13px; transition: background 0.15s; }
.project-team-chip:hover { background: var(--el-color-primary-light-8); }
.project-team-chip .chip-link { color: var(--el-color-primary); text-decoration: none; }
.project-team-chip .chip-link:hover { text-decoration: underline; }
.chip-actions { display: flex; align-items: center; gap: 2px; margin-left: 2px; }
.chip-action { font-size: 12px; color: var(--el-text-color-secondary); cursor: pointer; padding: 2px; border-radius: 3px; transition: all 0.15s; }
.chip-action:hover { color: var(--el-color-primary); background: rgba(64,158,255,0.1); }
.chip-remove:hover { color: #f56c6c; background: rgba(245,108,108,0.1); }
.add-chip { cursor: pointer; color: var(--el-text-color-secondary); background: var(--el-bg-color-page); border: 1px dashed var(--el-border-color); gap: 2px; }
.add-chip:hover { color: var(--el-color-primary); border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.no-team { font-size: 13px; color: var(--el-text-color-placeholder); }
.invite-section { margin-top: 12px; padding: 8px 12px; background: var(--el-bg-color-page); border-radius: 6px; display: flex; align-items: center; justify-content: space-between; }
.invite-label { font-size: 13px; color: var(--el-text-color-regular); }
.tags-container {
  min-height: 36px;
  display: flex;
  align-items: center;
}
.tags-list {
  display: flex;
  flex-wrap: wrap;
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
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}
.tags-empty-state:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
</style>
