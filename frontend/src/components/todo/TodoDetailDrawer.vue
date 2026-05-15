<template>
  <el-drawer
    v-model="visible"
    :title="$t('todo.taskDetail') + ' #' + todo?.id"
    size="520px"
    @closed="handleClose"
  >
    <div v-if="todo" class="drawer-body">
      <TodoFormFields
        v-model="form"
        :project-id="todo.projectId ?? undefined"
        ref="fieldsRef"
      />
      <div style="margin-top: 12px;">
        <el-button type="primary" :loading="saving" @click="save">{{ $t('todo.saveEdit') }}</el-button>
      </div>

      <el-divider />
      <h4>{{ $t('todo.subtask') }}</h4>
      <SubtaskList :todo-id="todo.id" :members="members" />

      <el-divider />
      <h4>{{ $t('todo.comment') }}</h4>
      <CommentList :todo-id="todo.id" :members="members" />

      <el-divider />
      <h4>{{ $t('todo.file') }}</h4>
      <AttachmentList :todo-id="todo.id" />
    </div>
    <el-empty v-else :description="$t('todo.taskNotFound')" />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useTodosStore } from '@/stores/todos'
import { tagsApi } from '@/api/tags'
import { teamsApi } from '@/api/teams'
import type { Tag, TeamMember } from '@/types'
import TodoFormFields from './TodoFormFields.vue'
import type { TodoFormData } from './TodoFormFields.vue'
import SubtaskList from './SubtaskList.vue'
import CommentList from '@/components/comment/CommentList.vue'
import AttachmentList from '@/components/attachment/AttachmentList.vue'

const props = defineProps<{ modelValue: boolean; todoId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const { t } = useI18n()
const todos = useTodosStore()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => {
    emit('update:modelValue', val)
    if (!val) router.push({ query: {} })
  }
})

const todo = computed(() => todos.items.find(t => t.id === props.todoId))
const members = ref<TeamMember[]>([])
const saving = ref(false)
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

watch(() => props.todoId, async (id) => {
  if (id && todo.value) {
    const t = todo.value
    form.value = {
      text: t.text,
      category: t.category,
      priority: t.priority,
      dueDate: t.dueDate ?? '',
      projectId: t.projectId ?? undefined,
      sectionId: t.sectionId ?? undefined,
      teamId: t.teamId ?? undefined,
      assigneeId: t.assigneeId ?? undefined,
      tagIds: []
    }
    await nextTick()
    fieldsRef.value?.refreshProjectDependencies()
    // 加载成员（给 SubtaskList / CommentList 用）
    members.value = []
    if (t.teamId) {
      try {
        const { data } = await teamsApi.detail(t.teamId)
        members.value = data.data.members ?? []
      } catch { members.value = [] }
    }
    // 加载已有标签
    try {
      const res = await tagsApi.getForTodo(t.id)
      form.value.tagIds = res.data.data.map((tag: Tag) => tag.id)
    } catch { form.value.tagIds = [] }
  }
}, { immediate: true })

function handleClose() {
  emit('update:modelValue', false)
}

async function save() {
  if (!todo.value || !form.value.text.trim()) return
  saving.value = true
  try {
    await todos.updateTodo(todo.value.id, {
      text: form.value.text.trim(),
      category: form.value.category,
      priority: form.value.priority,
      dueDate: form.value.dueDate || undefined,
      projectId: form.value.projectId,
      sectionId: form.value.sectionId,
      teamId: form.value.teamId || null,
      assigneeId: form.value.assigneeId || null
    })
    // 同步标签
    const currentTags = await tagsApi.getForTodo(todo.value.id)
    const currentIds = currentTags.data.data.map((tag: Tag) => tag.id)
    const toRemove = currentIds.filter(id => !form.value.tagIds.includes(id))
    const toAdd = form.value.tagIds.filter(id => !currentIds.includes(id))
    await Promise.all([
      ...toRemove.map(tagId => tagsApi.removeFromTodo(todo.value!.id, tagId)),
      ...toAdd.map(tagId => tagsApi.addToTodo(todo.value!.id, tagId))
    ])
    ElMessage.success(t('todo.saved'))
    if (todo.value.projectId) {
      await todos.fetchByProject(todo.value.projectId)
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('todo.saveFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.drawer-body { padding-bottom: 40px; }
</style>
