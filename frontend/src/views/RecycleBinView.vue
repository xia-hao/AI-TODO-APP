<template>
  <div class="recycle-bin">
    <h2>{{ $t('recycleBin.title') }}</h2>
    <p class="hint">{{ $t('recycleBin.hint') }}</p>
    <el-table :data="deletedItems" v-loading="loading" :empty-text="$t('recycleBin.empty')" style="width:100%">
      <el-table-column prop="text" :label="$t('todo.text')" min-width="200" />
      <el-table-column prop="projectName" :label="$t('todo.project')" width="120" />
      <el-table-column prop="teamName" :label="$t('todo.team')" width="120" />
      <el-table-column prop="category" :label="$t('todo.category')" width="100" />
      <el-table-column :label="$t('todo.priority')" width="80">
        <template #default="{ row }">
          <el-tag :type="row.priority === 'high' ? 'danger' : row.priority === 'medium' ? 'warning' : 'primary'" size="small">
            {{ ({high: $t('todo.high'), medium: $t('todo.medium'), low: $t('todo.low')} as any)[row.priority] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('recycleBin.deletedTime')" width="180">
        <template #default="{ row }">
          {{ row.deletedTime?.replace('T', ' ').slice(0, 16) }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('report.action')" width="200">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="handleRestore(row.id)">{{ $t('recycleBin.restore') }}</el-button>
          <el-button size="small" type="danger" @click="handlePermanentDelete(row.id)">{{ $t('recycleBin.permanentDelete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { recycleBinApi } from '@/api/recycleBin'
import type { Todo } from '@/types'

const { t } = useI18n()
const deletedItems = ref<Todo[]>([])
const loading = ref(false)

async function fetchDeleted() {
  loading.value = true
  try {
    const { data } = await recycleBinApi.listDeleted()
    deletedItems.value = data.data
  } finally {
    loading.value = false
  }
}

async function handleRestore(id: number) {
  await recycleBinApi.restore(id)
  ElMessage.success(t('recycleBin.restored'))
  fetchDeleted()
}

async function handlePermanentDelete(id: number) {
  await ElMessageBox.confirm(t('recycleBin.permanentDeleteConfirm'), t('app.confirm'))
  await recycleBinApi.permanentlyDelete(id)
  ElMessage.success(t('recycleBin.permanentDeleted'))
  fetchDeleted()
}

onMounted(fetchDeleted)
</script>

<style scoped>
.recycle-bin { padding: 20px; }
.hint { color: var(--el-text-color-secondary); font-size: 13px; margin-bottom: 16px; }
</style>
