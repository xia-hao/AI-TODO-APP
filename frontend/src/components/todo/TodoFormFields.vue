<template>
  <el-form :model="form" label-width="80px">
    <el-form-item :label="$t('todo.content')">
      <el-input v-model="form.text" :placeholder="$t('todo.placeholder')" />
    </el-form-item>
    <el-form-item :label="$t('todo.category')">
      <el-select v-model="form.category" style="width:100%">
        <el-option v-for="c in categories" :key="c" :label="categoryLabels[c] || c" :value="c" />
      </el-select>
    </el-form-item>
    <el-form-item :label="$t('todo.priority')">
      <el-select v-model="form.priority" style="width:100%">
        <el-option :label="$t('todo.high')" value="high" />
        <el-option :label="$t('todo.medium')" value="medium" />
        <el-option :label="$t('todo.low')" value="low" />
      </el-select>
    </el-form-item>
    <el-form-item :label="$t('todo.dueDate')">
      <el-date-picker
        v-model="form.dueDate"
        type="date"
        :placeholder="$t('todo.datePlaceholder')"
        value-format="YYYY-MM-DD"
        style="width:100%"
      />
    </el-form-item>

    <!-- 项目选择（创建时可自选，编辑时已关联则不可改） -->
    <el-form-item v-if="showProjectSelect" :label="$t('todo.project')">
      <el-select v-model="form.projectId" clearable :placeholder="$t('todo.projectPlaceholder')" @change="onProjectChange">
        <el-option v-for="p in filteredProjects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
    </el-form-item>

    <!-- 看板分区 -->
    <el-form-item v-if="currentProjectId" :label="$t('todo.section')">
      <el-select v-model="form.sectionId" :placeholder="$t('todo.sectionPlaceholder')" style="width:100%">
        <el-option v-for="sec in availableSections" :key="sec.id" :label="sec.name" :value="sec.id" />
      </el-select>
    </el-form-item>

    <!-- 团队选择 -->
    <el-form-item v-if="projectTeamOptions.length > 1" :label="$t('todo.team')">
      <el-select v-model="form.teamId" :placeholder="$t('todo.teamPlaceholder')" @change="onTeamChange">
        <el-option v-for="t in projectTeamOptions" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
    </el-form-item>

    <!-- 指定人 -->
    <el-form-item v-if="selectedTeamId && teamMembers.length > 0" :label="$t('todo.assignee')">
      <el-select v-model="form.assigneeId" clearable :placeholder="$t('todo.assigneePlaceholder')" style="width:100%">
        <el-option v-for="m in teamMembers" :key="m.userId" :label="m.displayName" :value="m.userId" />
      </el-select>
    </el-form-item>

    <!-- 标签 -->
    <el-form-item v-if="currentProjectId" :label="$t('todo.tags')">
      <el-select v-model="form.tagIds" multiple filterable :placeholder="$t('todo.tagPlaceholder')" style="width:100%">
        <el-option v-for="tag in availableTags" :key="tag.id" :label="tag.name" :value="tag.id">
          <span :style="{ color: tag.color }">●</span> {{ tag.name }}
        </el-option>
      </el-select>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useProjectsStore } from '@/stores/projects'
import { useTeamsStore } from '@/stores/teams'
import { sectionsApi } from '@/api/sections'
import { tagsApi } from '@/api/tags'
import type { Section, Tag, TeamMember } from '@/types'

const { t, locale } = useI18n()
const categoryLabels = computed<Record<string, string>>(() => {
  void locale.value
  return { '工作': t('todo.work'), '生活': t('todo.life'), '学习': t('todo.study'), '其他': t('todo.other') }
})

const props = defineProps<{
  modelValue: TodoFormData
  projectId?: number
  showProjectSelect?: boolean
  filterTeamId?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: TodoFormData]
}>()

export interface TodoFormData {
  text: string
  category: string
  priority: string
  dueDate: string
  projectId?: number
  sectionId?: number
  teamId?: number
  assigneeId?: number
  tagIds: number[]
}

const projectsStore = useProjectsStore()
const teamsStore = useTeamsStore()

const categories = ['工作', '生活', '学习', '其他']
const availableSections = ref<Section[]>([])
const availableTags = ref<Tag[]>([])
const teamMembers = ref<TeamMember[]>([])

const form = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 安全更新 form 字段（触发 v-model 更新）
function updateForm(patch: Partial<TodoFormData>) {
  emit('update:modelValue', { ...props.modelValue, ...patch })
}

const currentProjectId = computed(() => props.projectId ?? form.value.projectId)

const currentProject = computed(() => {
  const id = currentProjectId.value
  if (!id) return null
  return projectsStore.projects.find(p => p.id === id) ?? null
})

const projectTeamOptions = computed(() => currentProject.value?.teams ?? [])

const selectedTeamId = computed(() => form.value.teamId ?? currentProject.value?.teamIds?.[0] ?? null)

const filteredProjects = computed(() => {
  if (props.filterTeamId) {
    return projectsStore.projects.filter(p => p.teamIds?.includes(props.filterTeamId!))
  }
  return projectsStore.projects
})

// 在项目或 todo 变化时刷新依赖
watch(currentProjectId, (id) => {
  if (id) refreshProjectDependencies()
}, { immediate: true })

async function onTeamChange() {
  updateForm({ assigneeId: undefined })
  await loadTeamMembers()
}

async function loadTeamMembers() {
  const teamId = selectedTeamId.value
  if (teamId) {
    try {
      const team = await teamsStore.fetchDetail(teamId)
      teamMembers.value = team?.members ?? []
    } catch { teamMembers.value = [] }
  } else { teamMembers.value = [] }
}

async function onProjectChange(projectId: number | undefined) {
  if (projectId) {
    updateForm({
      projectId,
      teamId: currentProject.value?.teamIds?.[0] ?? undefined,
      sectionId: availableSections.value[0]?.id
    })
    await refreshProjectDependencies()
  } else {
    updateForm({
      projectId: undefined,
      sectionId: undefined,
      teamId: undefined,
      assigneeId: undefined,
      tagIds: []
    })
    availableSections.value = []
    availableTags.value = []
    teamMembers.value = []
  }
}

async function refreshProjectDependencies() {
  const projId = currentProjectId.value
  if (!projId) { availableSections.value = []; availableTags.value = []; teamMembers.value = []; return }
  try {
    const [sectionsRes, tagsRes] = await Promise.all([
      sectionsApi.list(projId),
      tagsApi.listForProject(projId)
    ])
    availableSections.value = sectionsRes.data.data
    availableTags.value = tagsRes.data.data
  } catch {
    availableSections.value = []
    availableTags.value = []
  }
  await loadTeamMembers()
}

// 暴露刷新方法供父组件调用
defineExpose({ refreshProjectDependencies, availableTags, teamMembers })
</script>
