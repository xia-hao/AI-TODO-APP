<template>
  <el-drawer
    v-model="visible"
    :title="$t('notification.title')"
    size="380px"
    direction="rtl"
  >
    <div class="notification-panel">
      <div v-if="loading" class="loading-wrap">
        <el-skeleton :rows="4" animated />
      </div>

      <div v-else-if="notifyStore.list.length === 0" class="empty">
        <el-empty :description="$t('notification.noNotification')" :image-size="80" />
      </div>

      <div v-else class="notify-list">
        <div
          v-for="item in notifyStore.list"
          :key="item.id"
          class="notify-item"
          :class="{ unread: !item.isRead }"
          @click="onClick(item)"
        >
          <div class="notify-dot" v-if="!item.isRead" />
          <div class="notify-body">
            <div class="notify-title">{{ item.title }}</div>
            <div class="notify-content">{{ item.content }}</div>
            <div class="notify-time">{{ formatTime(item.createTime) }}</div>
          </div>
          <el-button
            v-if="!item.isRead"
            link
            type="primary"
            size="small"
            @click.stop="markRead(item.id)"
          >{{ $t('notification.markRead') }}</el-button>
        </div>
      </div>

      <div v-if="notifyStore.list.length > 0" class="notify-footer">
        <el-button type="primary" link @click="markAllRead">
          {{ $t('notification.markAllRead') }}
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useNotificationsStore } from '@/stores/notifications'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const notifyStore = useNotificationsStore()
const loading = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

watch(visible, async (val) => {
  if (val) {
    loading.value = true
    await notifyStore.fetchUnread()
    loading.value = false
  }
})

function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return t('notification.justNow')
  if (minutes < 60) return t('notification.minutesAgo', { minutes })
  if (hours < 24) return t('notification.hoursAgo', { hours })
  if (days < 7) return t('notification.daysAgo', { days })
  return date.toLocaleString('zh-CN')
}

async function onClick(item: any) {
  if (!item.isRead) {
    await notifyStore.markRead(item.id)
  }
  if (item.targetUrl) {
    visible.value = false
    router.push(item.targetUrl)
  }
}

async function markRead(id: number) {
  await notifyStore.markRead(id)
}

async function markAllRead() {
  await notifyStore.markAllRead()
}
</script>

<style scoped>
.notification-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.loading-wrap {
  padding: 16px;
}

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.notify-list {
  flex: 1;
  overflow-y: auto;
}

.notify-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background 0.15s;
}

.notify-item:hover {
  background: var(--el-fill-color-light);
}

.notify-item.unread {
  background: var(--el-color-primary-light-9);
}

.notify-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-primary);
  margin-top: 6px;
  flex-shrink: 0;
}

.notify-body {
  flex: 1;
  min-width: 0;
}

.notify-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.notify-content {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
  word-break: break-word;
}

.notify-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.notify-footer {
  padding: 12px 16px;
  text-align: center;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>