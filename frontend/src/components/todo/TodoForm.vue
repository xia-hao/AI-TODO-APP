<template>
  <div class="todo-form-trigger">
    <el-button v-if="!todo" type="primary" :icon="Plus" @click="open">{{ $t('todo.add') }}</el-button>

    <el-dialog
      v-model="visible"
      :title="todo ? $t('common.edit') : $t('todo.add')"
      width="500px"
      @closed="reset"
    >
      <TodoFormFields
        v-model="form"
        :project-id="form.projectId"
        :show-project-select="!todo && allowProjectSelect && !props.projectId"
        :filter-team-id="props.filterTeamId"
        ref="fieldsRef"
      />
      <template #footer>
        <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" @click="submit">
          {{ todo ? $t('common.save') : $t('common.add') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import { useTodosStore } from '@/stores/todos'
import { tagsApi } from '@/api/tags'
import type { Todo, Tag } from '@/types'
import TodoFormFields from './TodoFormFields.vue'
import type { TodoFormData } from './TodoFormFields.vue'

const { t } = useI18n()

const props = defineProps<{
  todo?: Todo | null
  projectId?: number
  allowProjectSelect?: boolean
  filterTeamId?: number
}>()

const emit = defineEmits<{ saved: [] }>()

const todos = useTodosStore()

const visible = ref(false)
const loading = ref(false)
const fieldsRef = ref<InstanceType<typeof TodoFormFields> | null>(null)

const form = ref<TodoFormData>({
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

function open() {
  reset()
  visible.value = true
}

async function openForEdit(todoData: Todo) {
  form.value = {
    text: todoData.text,
    category: todoData.category,
    priority: todoData.priority,
    dueDate: todoData.dueDate ?? '',
    projectId: todoData.projectId ?? undefined,
    sectionId: todoData.sectionId ?? undefined,
    teamId: todoData.teamId ?? undefined,
    assigneeId: todoData.assigneeId ?? undefined,
    tagIds: []
  }
  visible.value = true
  // 等待 DOM 渲染后加载依赖
  await nextTick()
  fieldsRef.value?.refreshProjectDependencies()
  // 加载已有标签
  await loadTodoTags(todoData.id)
}

async function loadTodoTags(todoId: number) {
  try {
    const res = await tagsApi.getForTodo(todoId)
    form.value.tagIds = res.data.data.map((t: Tag) => t.id)
  } catch { form.value.tagIds = [] }
}

function reset() {
  form.value = {
    text: '',
    category: '其他',
    priority: 'medium',
    dueDate: '',
    projectId: props.projectId,
    sectionId: undefined,
    teamId: undefined,
    assigneeId: undefined,
    tagIds: []
  }
  loading.value = false
}

async function submit() {
  if (!form.value.text.trim()) return
  loading.value = true
  try {
    const params: any = {
      text: form.value.text.trim(),
      category: form.value.category,
      priority: form.value.priority,
      dueDate: form.value.dueDate || undefined,
      projectId: form.value.projectId,
      sectionId: form.value.sectionId,
      teamId: form.value.teamId || null,
      assigneeId: form.value.assigneeId || null
    }

    if (props.todo) {
      await todos.updateTodo(props.todo.id, params)
      // 同步标签
      await syncTags(props.todo.id, form.value.tagIds)
      ElMessage.success(t('todo.saved'))
      emit('saved')
    } else {
      const created = await todos.createTodo(params)
      if (form.value.tagIds.length > 0 && created) {
        await Promise.all(form.value.tagIds.map(tagId => tagsApi.addToTodo(created.id, tagId)))
      }
      ElMessage.success(t('todo.added'))
    }

    visible.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || (props.todo ? t('todo.saveFailed') : t('todo.addFailed')))
  } finally {
    loading.value = false
  }
}

async function syncTags(todoId: number, selectedIds: number[]) {
  const currentTags = await tagsApi.getForTodo(todoId)
  const currentIds = currentTags.data.data.map((t: Tag) => t.id)
  const toRemove = currentIds.filter(id => !selectedIds.includes(id))
  const toAdd = selectedIds.filter(id => !currentIds.includes(id))
  await Promise.all([
    ...toRemove.map(tagId => tagsApi.removeFromTodo(todoId, tagId)),
    ...toAdd.map(tagId => tagsApi.addToTodo(todoId, tagId))
  ])
}

defineExpose({ open, openForEdit })
</script>

<style scoped>
.todo-form-trigger { margin-bottom: 16px; }
</style>
