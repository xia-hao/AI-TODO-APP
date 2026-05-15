<template>
  <div class="todo-filters">
    <el-input v-model="todos.filters.q" :placeholder="$t('todo.searchPlaceholder')" prefix-icon="Search" clearable style="width:180px" />
    <el-select v-model="todos.filters.status" style="width:100px">
      <el-option :label="$t('todo.filterAll')" value="" />
      <el-option :label="$t('todo.filterActive')" value="active" />
      <el-option :label="$t('todo.filterCompleted')" value="completed" />
    </el-select>
    <el-select v-model="todos.filters.category" style="width:100px">
      <el-option :label="$t('todo.allCategories')" value="" />
      <el-option v-for="c in categories" :key="c" :label="categoryLabels[c] || c" :value="c" />
    </el-select>
    <el-select v-model="todos.filters.tagIds" multiple collapse-tags :placeholder="$t('todo.tag')" style="width:120px">
      <el-option v-for="tag in availableTags" :key="tag.id" :label="tag.name" :value="tag.id">
        <span :style="{color: tag.color}">●</span> {{ tag.name }}
      </el-option>
    </el-select>
    <el-date-picker
      v-model="dateRange"
      type="daterange"
      :range-separator="$t('todo.dateRange')"
      :start-placeholder="$t('todo.dateStart')"
      :end-placeholder="$t('todo.dateEnd')"
      value-format="YYYY-MM-DD"
      style="width:220px"
      @change="onDateChange"
    />
    <el-button @click="handleExport" :loading="exporting" :icon="Download">{{ $t('todo.export') }}</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { useTodosStore } from '@/stores/todos'
import { tagsApi } from '@/api/tags'
import type { Tag } from '@/types'

const todos = useTodosStore()
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
const categories = ['工作', '生活', '学习', '其他']
const availableTags = ref<Tag[]>([])
const exporting = ref(false)
const dateRange = ref<string[]>([])

onMounted(async () => {
  try {
    const pid = todos.currentProjectId
    if (pid) {
      const { data } = await tagsApi.listForProject(pid)
      availableTags.value = data.data
    }
  } catch (e) { /* ignore */ }
})

function onDateChange(val: string[] | null) {
  if (val && val.length === 2) {
    todos.filters.dateFrom = val[0]
    todos.filters.dateTo = val[1]
  } else {
    todos.filters.dateFrom = ''
    todos.filters.dateTo = ''
  }
}

async function handleExport() {
  exporting.value = true
  try {
    await todos.exportExcel()
    ElMessage.success(t('todo.exportSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('todo.exportFailed'))
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.todo-filters { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; align-items: center; }
</style>