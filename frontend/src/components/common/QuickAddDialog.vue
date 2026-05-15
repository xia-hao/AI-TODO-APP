<template>
  <el-dialog v-model="visible" :title="$t('todo.quickAddTitle')" width="400px" @closed="reset">
    <el-form @submit.prevent>
      <el-form-item>
        <el-input
          v-model="title"
          :placeholder="$t('todo.quickAddPlaceholder')"
          @keyup.enter="submit"
          autofocus
        />
      </el-form-item>
      <el-form-item>
        <el-select v-model="projectId" clearable :placeholder="$t('todo.projectPlaceholder')">
          <el-option
            v-for="p in projectsStore.projects"
            :key="p.id"
            :label="p.name"
            :value="p.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="loading" @click="submit">{{ $t('common.add') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useProjectsStore } from '@/stores/projects'
import { useTodosStore } from '@/stores/todos'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const projectsStore = useProjectsStore()
const todos = useTodosStore()
const title = ref('')
const projectId = ref<number | undefined>()
const loading = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

function reset() {
  title.value = ''
  projectId.value = undefined
}

async function submit() {
  if (!title.value.trim()) return
  loading.value = true
  try {
    await todos.createTodo({
      text: title.value.trim(),
      category: '其他',
      priority: 'medium',
      projectId: projectId.value,
      sectionId: projectId.value
        ? projectsStore.currentProject?.sections?.[0]?.id
        : undefined,
    })
    ElMessage.success(t('todo.quickAddCreated'))
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('todo.quickAddFailed'))
  } finally {
    loading.value = false
  }
}
</script>