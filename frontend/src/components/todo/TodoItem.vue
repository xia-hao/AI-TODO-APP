<template>
  <div v-if="todo"
    class="todo-item"
    :class="[
      `priority-${todo.priority}`,
      { completed: todo.completed, overdue: isOverdue }
    ]"
    @dblclick="openEdit"
    :data-todo-id="todo.id"
  >
    <el-icon class="drag-handle"><Grid /></el-icon>

    <el-checkbox
      v-if="selectable"
      :model-value="selected"
      @update:model-value="$emit('update:selected', $event)"
      @click.stop
      style="margin-right: 8px"
    />

    <div class="todo-body">
      <span class="todo-text">{{ todo.text }}</span>
      <div class="todo-meta">
        <el-tag size="small" type="info">{{ categoryLabels[todo.category] || todo.category }}</el-tag>
        <el-tag size="small" :type="priorityType">{{ priorityLabel }}</el-tag>
        <span v-if="todo.dueDate" class="due-date">
          {{ todo.dueDate }}<span v-if="isOverdue" class="overdue-label">{{ $t('todo.expired') }}</span>
        </span>
        <el-tag v-if="todo.assigneeId" size="small" type="success">
          {{ todo.assigneeName }}
        </el-tag>
        <el-tag
          v-for="tag in todoTags"
          :key="tag.id"
          size="small"
          :style="{ backgroundColor: tag.color, color: '#fff', border: 'none' }"
          effect="dark"
          style="margin-left: 4px"
        >
          {{ tag.name }}
        </el-tag>
      </div>
    </div>

    <el-dropdown trigger="click" @click.stop>
      <el-icon class="action-trigger"><MoreFilled /></el-icon>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="handleToggle">
            {{ todo.completed ? $t('todo.unmarkComplete') : $t('todo.markComplete') }}
          </el-dropdown-item>
          <el-dropdown-item @click="openEdit" divided>{{ $t('common.edit') }}</el-dropdown-item>
          <el-dropdown-item @click="handleDelete" divided>{{ $t('common.delete') }}</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="$t('todo.edit')" width="600px" @closed="resetEdit" @click.stop>
      <el-tabs v-model="editTab">
        <el-tab-pane :label="$t('todo.basicInfo')" name="basic">
          <TodoFormFields
            v-model="editForm"
            :project-id="props.todo.projectId ?? undefined"
            ref="formFieldsRef"
          />
        </el-tab-pane>
        <el-tab-pane :label="$t('todo.subtask')" name="subtasks">
          <SubtaskList :todo-id="todo.id" :members="teamMembers" />
        </el-tab-pane>
        <el-tab-pane :label="$t('todo.comment')" name="comments">
          <CommentList :todo-id="todo.id" :members="teamMembers" />
        </el-tab-pane>
        <el-tab-pane :label="$t('todo.file')" name="attachments">
          <AttachmentList :todo-id="todo.id" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="editVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Grid, MoreFilled } from '@element-plus/icons-vue'
import type { Todo, Tag, TeamMember } from '@/types'
import { useTodosStore } from '@/stores/todos'
import { tagsApi } from '@/api/tags'
import { teamsApi } from '@/api/teams'
import TodoFormFields from './TodoFormFields.vue'
import type { TodoFormData } from './TodoFormFields.vue'
import SubtaskList from './SubtaskList.vue'
import CommentList from '@/components/comment/CommentList.vue'
import AttachmentList from '@/components/attachment/AttachmentList.vue'

const { t, locale } = useI18n()

const categoryLabels = computed<Record<string, string>>(() => {
  void locale.value
  return {
    '工作': t('todo.work'),
    '生活': t('todo.life'),
    '学习': t('todo.study'),
    '其他': t('todo.other')
  }
})

const props = defineProps<{
  todo: Todo
  selectable?: boolean
  selected?: boolean
}>()

defineEmits<{
  'update:selected': [value: boolean]
}>()

const todos = useTodosStore()
const editVisible = ref(false)
const saving = ref(false)
const editTab = ref('basic')

const todoTags = ref<Tag[]>([])
const teamMembers = ref<TeamMember[]>([])
const formFieldsRef = ref<InstanceType<typeof TodoFormFields> | null>(null)

const editForm = ref<TodoFormData>({
  text: '',
  category: '其他',
  priority: 'medium',
  dueDate: '',
  projectId: undefined,
  sectionId: undefined,
  teamId: undefined,
  assigneeId: undefined,
  tagIds: []
})

const isOverdue = computed(() =>
  !props.todo.completed &&
  !!props.todo.dueDate &&
  props.todo.dueDate < new Date().toISOString().slice(0, 10)
)

const priorityLabel = computed(() => ({ high: t('todo.high'), medium: t('todo.medium'), low: t('todo.low') }[props.todo.priority]))
const priorityType = computed(() => ({ high: 'danger', medium: 'warning', low: 'primary' }[props.todo.priority] as any))

onMounted(async () => {
  fetchTodoTags()
})

async function fetchTodoTags() {
  try {
    const { data } = await tagsApi.getForTodo(props.todo.id)
    todoTags.value = data.data
  } catch { todoTags.value = [] }
}

async function openEdit() {
  editForm.value = {
    text: props.todo.text,
    category: props.todo.category,
    priority: props.todo.priority,
    dueDate: props.todo.dueDate ?? '',
    projectId: props.todo.projectId ?? undefined,
    sectionId: props.todo.sectionId ?? undefined,
    teamId: props.todo.teamId ?? undefined,
    assigneeId: props.todo.assigneeId ?? undefined,
    tagIds: []
  }

  // 加载团队成员
  teamMembers.value = []
  if (props.todo.teamId) {
    try {
      const { data } = await teamsApi.detail(props.todo.teamId)
      teamMembers.value = data.data.members ?? []
    } catch { /* ignore */ }
  }

  editTab.value = 'basic'
  editVisible.value = true

  // 等待 DOM 渲染后刷新字段依赖（分区、标签、成员）
  await nextTick()
  formFieldsRef.value?.refreshProjectDependencies()

  // 加载已有标签
  try {
    const res = await tagsApi.getForTodo(props.todo.id)
    editForm.value.tagIds = res.data.data.map((t: Tag) => t.id)
  } catch { editForm.value.tagIds = [] }
}

function resetEdit() {
  saving.value = false
}

async function submitEdit() {
  if (!editForm.value.text.trim()) return
  saving.value = true
  try {
    await todos.updateTodo(props.todo.id, {
      text: editForm.value.text.trim(),
      category: editForm.value.category,
      priority: editForm.value.priority,
      dueDate: editForm.value.dueDate || undefined,
      projectId: editForm.value.projectId,
      sectionId: editForm.value.sectionId,
      teamId: editForm.value.teamId || null,
      assigneeId: editForm.value.assigneeId || null
    })

    // 同步标签
    const currentTags = await tagsApi.getForTodo(props.todo.id)
    const currentIds = currentTags.data.data.map((t: Tag) => t.id)
    const toRemove = currentIds.filter(id => !editForm.value.tagIds.includes(id))
    const toAdd = editForm.value.tagIds.filter(id => !currentIds.includes(id))
    await Promise.all([
      ...toRemove.map(id => tagsApi.removeFromTodo(props.todo.id, id)),
      ...toAdd.map(id => tagsApi.addToTodo(props.todo.id, id))
    ])

    // 设置提醒
    // 注意：reminder 不在 TodoFormFields 中，保留旧逻辑但不再通过表单 UI 设置

    editVisible.value = false
    ElMessage.success(t('todo.saved'))
    fetchTodoTags()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('todo.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function handleToggle() {
  try {
    await todos.toggleComplete(props.todo.id)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('common.operationFailed'))
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(t('app.deleteConfirm'), t('app.hint'), { type: 'warning' })
  } catch { return }
  try {
    await todos.deleteTodo(props.todo.id)
    ElMessage.success(t('todo.deleted'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('todo.deleteFailed'))
  }
}
</script>

<style scoped>
.todo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-left: 4px solid #ddd;
  border-radius: 6px;
  background: var(--el-bg-color);
  margin-bottom: 8px;
  transition: box-shadow 0.2s;
  cursor: default;
}
.todo-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.priority-high {
  border-left-color: var(--el-color-danger);
}
.priority-medium {
  border-left-color: var(--el-color-warning);
}
.priority-low {
  border-left-color: var(--el-color-primary);
}
.todo-item.overdue {
  background: var(--el-color-danger-light-9);
}
.drag-handle {
  cursor: grab;
  color: var(--el-text-color-placeholder);
}
.todo-body {
  flex: 1;
  min-width: 0;
}
.todo-text {
  display: block;
  word-break: break-word;
}
.completed .todo-text {
  text-decoration: line-through;
  color: var(--el-text-color-placeholder);
}
.todo-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 4px;
  flex-wrap: wrap;
}
.due-date {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.overdue-label {
  color: var(--el-color-danger);
}
.action-trigger {
  cursor: pointer;
  color: var(--el-text-color-secondary);
  font-size: 20px;
  transform: rotate(90deg);
}
.action-trigger:hover {
  color: var(--el-color-primary);
}
</style>
