<template>
  <div class="comment-item">
    <div class="comment-header">
      <span class="author">{{ comment.displayName }}</span>
      <span class="time">{{ formatTime(comment.createTime) }}</span>
      <el-button
        v-if="comment.userId === auth.user?.id"
        link
        type="danger"
        size="small"
        @click="$emit('delete', comment.id)"
      >{{ $t('common.delete') }}</el-button>
    </div>
    <MentionContent :content="comment.content" />
    <el-button link size="small" @click="handleReply">{{ $t('todo.reply') }}</el-button>

    <div v-if="comment.children?.length" class="comment-children">
      <CommentItem
        v-for="child in comment.children"
        :key="child.id"
        :comment="child"
        @delete="$emit('delete', $event)"
        @reply="(p: any) => $emit('reply', p)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Comment } from '@/types'
import { useAuthStore } from '@/stores/auth'
import MentionContent from './MentionContent.vue'

const props = defineProps<{ comment: Comment }>()
const emit = defineEmits<{
  delete: [id: number]
  reply: [payload: { id: number; displayName: string }]
}>()
const auth = useAuthStore()

function handleReply() {
  emit('reply', { id: props.comment.id, displayName: props.comment.displayName })
}

function formatTime(dateStr: string) {
  return new Date(dateStr).toLocaleString('zh-CN')
}
</script>

<style scoped>
.comment-item { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-light); }
.comment-header { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.author { font-weight: 600; color: var(--el-text-color-primary); }
.time { color: var(--el-text-color-secondary); }
.comment-content { margin: 4px 0; font-size: 14px; }
.comment-children { margin-left: 24px; }
</style>
