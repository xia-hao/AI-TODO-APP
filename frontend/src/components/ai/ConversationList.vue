<template>
  <div class="conversation-sidebar">
    <div class="sidebar-header">
      <span class="sidebar-title">会话</span>
      <el-button :icon="Plus" circle size="small" @click="emit('create')" :disabled="loading" />
    </div>
    <div class="sidebar-search">
      <el-input
        v-model="searchQuery"
        placeholder="搜索会话..."
        :prefix-icon="Search"
        clearable
        size="small"
      />
    </div>
    <div class="sidebar-items" v-if="conversationsLoading">
      <div v-for="i in 5" :key="i" class="sidebar-item sk-item">
        <el-skeleton animated :rows="1" />
      </div>
    </div>
    <div class="sidebar-items" v-else-if="filteredConversations.length > 0">
      <div
        v-for="conv in filteredConversations"
        :key="conv.id"
        class="sidebar-item"
        :class="{ active: conv.id === currentId }"
        @click="emit('select', conv.id)"
      >
        <div class="item-content">
          <div class="item-title">{{ conv.title }}</div>
          <div class="item-time">{{ formatTime(conv.updateTime) }}</div>
        </div>
        <div class="item-actions" @click.stop>
          <el-button :icon="Edit" text size="small" @click="startRename(conv)" />
          <el-button :icon="Delete" text size="small" type="danger" @click="emit('delete', conv.id)" />
        </div>
      </div>
    </div>
    <div v-else class="sidebar-empty">
      <p v-if="conversations.length > 0 && searchQuery">未找到匹配的会话</p>
      <p v-else>暂无会话</p>
    </div>

    <!-- Rename dialog -->
    <el-dialog v-model="renameVisible" title="重命名" width="350px" :close-on-click-modal="false">
      <el-input v-model="renameValue" maxlength="50" show-word-limit @keyup.enter="confirmRename" />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRename">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Plus, Edit, Delete, Search } from '@element-plus/icons-vue'
import type { Conversation } from '@/api/ai'

const props = defineProps<{
  conversations: Conversation[]
  currentId: number | null
  loading: boolean
  conversationsLoading: boolean
}>()

const emit = defineEmits<{
  create: []
  select: [id: number]
  delete: [id: number]
  rename: [conv: Conversation]
}>()

const searchQuery = ref('')

const filteredConversations = computed(() => {
  if (!searchQuery.value.trim()) return props.conversations
  const q = searchQuery.value.trim().toLowerCase()
  return props.conversations.filter((c) => c.title.toLowerCase().includes(q))
})

const renameVisible = ref(false)
const renameValue = ref('')
const renameTarget = ref<Conversation | null>(null)

function startRename(conv: Conversation) {
  renameTarget.value = conv
  renameValue.value = conv.title
  renameVisible.value = true
}

function confirmRename() {
  if (renameTarget.value && renameValue.value.trim()) {
    emit('rename', { ...renameTarget.value, title: renameValue.value.trim() })
  }
  renameVisible.value = false
  renameTarget.value = null
}

function formatTime(timeStr: string): string {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 86400000) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (diff < 172800000) return '昨天'
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
</script>

<style scoped>
.conversation-sidebar {
  width: 240px;
  min-width: 240px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-blank);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.sidebar-search {
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.sidebar-items {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.sidebar-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.15s;
}

.sidebar-item:hover,
.sidebar-item.active {
  background: var(--el-fill-color-light);
}

.item-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.item-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  user-select: text !important;
}

.item-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
  user-select: text !important;
}

.item-actions {
  display: none;
  gap: 2px;
  flex-shrink: 0;
}

.sidebar-item:hover .item-actions {
  display: flex;
}

.sidebar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.sk-item {
  pointer-events: none;
  padding: 10px 12px;
}
</style>
