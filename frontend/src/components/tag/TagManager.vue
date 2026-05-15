<template>
  <div class="tag-manager">
    <div class="tag-header">
      <span>{{ $t('tag.manager') }}</span>
      <el-button link :icon="Plus" @click="showCreate = true" />
    </div>
    <div class="tag-list">
      <el-tag
        v-for="tag in tags"
        :key="tag.id"
        :color="tag.color"
        closable
        @close="handleDelete(tag.id)"
        style="margin: 4px"
      >
        {{ tag.name }}
      </el-tag>
    </div>

    <el-dialog v-model="showCreate" :title="$t('tag.create')" width="300px">
      <el-form>
        <el-form-item :label="$t('tag.name')">
          <el-input v-model="newName" />
        </el-form-item>
        <el-form-item :label="$t('tag.color')">
          <el-color-picker v-model="newColor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">{{ $t('tag.cancel') }}</el-button>
        <el-button type="primary" @click="doCreate">{{ $t('tag.createLabel') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import type { Tag } from '@/types'
import { tagsApi } from '@/api/tags'

const { t } = useI18n()

const props = defineProps<{ projectId: number }>()

const tags = ref<Tag[]>([])
const showCreate = ref(false)
const newName = ref('')
const newColor = ref('#409eff')

async function fetch() {
  const { data } = await tagsApi.listForProject(props.projectId)
  tags.value = data.data
}

async function doCreate() {
  if (!newName.value.trim()) return
  try {
    await tagsApi.createProjectTag(props.projectId, newName.value.trim(), newColor.value)
    showCreate.value = false
    newName.value = ''
    ElMessage.success(t('tag.created'))
    await fetch()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('tag.createFailed'))
  }
}

async function handleDelete(tagId: number) {
  try {
    await tagsApi.delete(tagId)
    tags.value = tags.value.filter(t => t.id !== tagId)
    ElMessage.success(t('tag.deleted'))
  } catch (e: any) {
    ElMessage.error(t('tag.deleteFailed'))
  }
}

onMounted(fetch)
</script>

<style scoped>
.tag-manager { padding: 8px 12px; }
.tag-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-weight: 600; }
</style>