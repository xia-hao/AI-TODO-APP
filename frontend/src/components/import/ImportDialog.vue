<template>
  <el-dialog v-model="visible" :title="$t('project.importTitle')" width="500px">
    <el-steps :active="step" finish-status="success" simple style="margin-bottom: 20px">
      <el-step :title="$t('project.importStepFile')" />
      <el-step :title="$t('project.importStepPreview')" />
      <el-step :title="$t('project.importStepDone')" />
    </el-steps>

    <template v-if="step === 0">
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :accept="'.csv,.json'"
        :on-change="handleFileChange"
      >
        <el-icon size="40" color="#409eff"><UploadFilled /></el-icon>
        <div style="margin-top: 8px">{{ $t('project.importDrag') }}</div>
        <template #tip>
          <div style="font-size: 12px; color: #909399">
            <div>{{ $t('project.importFormatCSV') }}</div>
            <div>{{ $t('project.importFormatJSON') }}</div>
          </div>
        </template>
      </el-upload>
      <el-select v-model="importProjectId" :placeholder="$t('project.importTargetProject')" style="width:100%;margin-top:12px">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
    </template>

    <template v-if="step === 1">
      <el-table :data="preview" max-height="300" border>
        <el-table-column prop="text" :label="$t('todo.text')" min-width="150" />
        <el-table-column prop="category" :label="$t('todo.category')" width="80" />
        <el-table-column prop="priority" :label="$t('todo.priority')" width="70" />
        <el-table-column prop="dueDate" :label="$t('todo.dueDate')" width="100" />
      </el-table>
      <div style="margin-top:8px;color:#909399;font-size:13px">{{ $t('project.importCount', { n: preview.length }) }}</div>
    </template>

    <template v-if="step === 2">
      <el-result icon="success" :title="$t('project.importDone')" :sub-title="$t('project.importSuccess', { n: importCount })" />
    </template>

    <template #footer>
      <el-button v-if="step < 2" @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button v-if="step === 0" type="primary" @click="previewData" :disabled="!importProjectId || !parsedData">
        {{ $t('project.importPreview') }}
      </el-button>
      <el-button v-if="step === 1" type="primary" @click="doImport" :loading="importing">
        {{ $t('project.importConfirm') }}
      </el-button>
      <el-button v-if="step === 2" type="primary" @click="visible = false">{{ $t('project.importDone') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { projectsApi } from '@/api/projects'
import { todosApi } from '@/api/todos'
import type { Project } from '@/types'

const { t } = useI18n()

const visible = defineModel<boolean>('modelValue')
const emit = defineEmits<{ imported: [] }>()

const step = ref(0)
const projects = ref<Project[]>([])
const importProjectId = ref<number | null>(null)
const parsedData = ref<any[]>([])
const preview = ref<any[]>([])
const importing = ref(false)
const importCount = ref(0)

function handleFileChange(file: any) {
  const reader = new FileReader()
  reader.onload = (e) => {
    const content = e.target?.result as string
    try {
      if (file.name.endsWith('.json')) {
        parsedData.value = JSON.parse(content)
      } else {
        parsedData.value = parseCSV(content)
      }
    } catch {
      ElMessage.error(t('project.importParseFailed'))
    }
  }
  reader.readAsText(file.raw)
}

function parseCSV(content: string): any[] {
  const lines = content.trim().split('\n')
  if (lines.length < 2) return []
  const headers = lines[0].split(',').map(h => h.trim())
  return lines.slice(1).map(line => {
    const vals = line.split(',').map(v => v.trim())
    const obj: any = {}
    headers.forEach((h, i) => { obj[h] = vals[i] || '' })
    return obj
  }).filter(item => item.text)
}

function previewData() {
  preview.value = parsedData.value.slice(0, 100)
  step.value = 1
}

async function doImport() {
  if (!importProjectId.value) return
  importing.value = true
  try {
    const { data } = await todosApi.import(importProjectId.value, parsedData.value)
    importCount.value = data.data.length
    step.value = 2
    emit('imported')
  } catch {
    ElMessage.error(t('project.importFailed'))
  } finally {
    importing.value = false
  }
}

onMounted(async () => {
  const { data } = await projectsApi.list()
  projects.value = data.data
})
</script>
