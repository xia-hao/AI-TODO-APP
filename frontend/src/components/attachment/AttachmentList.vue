<template>
  <div class="attachment-list">
    <div class="attachment-header">
      <span>{{ $t('todo.file') }}</span>
      <el-upload
        :action="uploadUrl"
        :headers="uploadHeaders"
        :show-file-list="false"
        :on-success="onSuccess"
        :on-error="onError"
        :before-upload="beforeUpload"
      >
        <el-button link :icon="Upload" />
      </el-upload>
    </div>

    <div v-if="attachments.length === 0" class="empty">{{ $t('todo.noFile') }}</div>

    <div v-for="att in attachments" :key="att.id" class="attachment-item">
      <el-icon><Document /></el-icon>
      <a :href="downloadUrl(att.id)" class="attachment-name">{{ att.fileName }}</a>
      <span class="file-size">{{ formatSize(att.fileSize) }}</span>
      <el-button link type="danger" size="small" @click="remove(att.id)">{{ $t('common.delete') }}</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Document } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import type { Attachment } from '@/types'
import { attachmentsApi } from '@/api/attachments'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()

const props = defineProps<{ todoId: number }>()
const auth = useAuthStore()
const attachments = ref<Attachment[]>([])

const uploadUrl = computed(() => `/api/todos/${props.todoId}/attachments`)
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${auth.token}`
}))

function downloadUrl(attachmentId: number) {
  return attachmentsApi.getDownloadUrl(props.todoId, attachmentId)
}

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

async function fetch() {
  try {
    const { data } = await attachmentsApi.list(props.todoId)
    attachments.value = data.data
  } catch (e: any) {
    // 静默失败
  }
}

function onSuccess(_response: any, _file: any) {
  ElMessage.success(t('todo.fileUploadSuccess'))
  fetch()
}

function onError(_err: any) {
  ElMessage.error(t('todo.fileUploadFailed'))
}

function beforeUpload(file: File) {
  const maxSize = 20 * 1024 * 1024 // 20MB
  if (file.size > maxSize) {
    ElMessage.error(t('todo.fileSizeLimit'))
    return false
  }
  return true
}

async function remove(attachmentId: number) {
  try {
    await attachmentsApi.delete(props.todoId, attachmentId)
    attachments.value = attachments.value.filter(a => a.id !== attachmentId)
    ElMessage.success(t('todo.fileDeleted'))
  } catch (e: any) {
    ElMessage.error(t('todo.fileDeleteFailed'))
  }
}

onMounted(fetch)
</script>

<style scoped>
.attachment-list { margin-top: 16px; }
.attachment-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.attachment-item { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
.attachment-name { flex: 1; }
.file-size { font-size: 12px; color: var(--el-text-color-secondary); }
.empty { color: var(--el-text-color-placeholder); font-size: 13px; }
</style>