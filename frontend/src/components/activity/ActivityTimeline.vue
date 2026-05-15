<template>
  <div class="activity-timeline">
    <el-timeline>
      <el-timeline-item
        v-for="log in logs" :key="log.id"
        :timestamp="formatTime(log.createTime)"
        placement="top"
      >
        <div class="log-item">
          <span class="log-user">{{ log.userDisplayName }}</span>
          <span class="log-action">{{ actionMap[log.action] || log.action }}</span>
          <span class="log-detail">{{ log.detail }}</span>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-if="!loading && !logs.length" :description="$t('activity.noActivity')" />
    <div v-if="loading" class="loading">{{ $t('activity.loading') }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { projectsApi } from '@/api/activities'
import type { ActivityLog } from '@/types'

const props = defineProps<{ projectId: number }>()
const logs = ref<ActivityLog[]>([])
const loading = ref(false)

const { t } = useI18n()

const actionMap = computed<Record<string, string>>(() => ({
  create: t('activity.create'),
  update: t('activity.update'),
  delete: t('activity.delete'),
  complete: t('activity.complete'),
  move: t('activity.move')
}))

function formatTime(t: string) {
  return t?.replace('T', ' ').slice(0, 16) || ''
}

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await projectsApi.getActivities(props.projectId)
    logs.value = data.data
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.activity-timeline { padding: 16px; }
.log-item { font-size: 14px; line-height: 1.6; }
.log-user { font-weight: 600; color: var(--el-color-primary); margin-right: 4px; }
.log-action { color: var(--el-text-color-regular); margin-right: 4px; }
.log-detail { color: var(--el-text-color-secondary); }
.loading { text-align: center; color: var(--el-text-color-secondary); padding: 20px; }
</style>
