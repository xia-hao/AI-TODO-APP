<template>
  <div class="comment-list">
    <!-- 新评论输入 -->
    <div class="comment-input">
      <MentionInput
        v-model="newComment"
        :members="members"
        :placeholder="$t('todo.commentMentionPlaceholder')"
        :max-length="MAX_LENGTH"
      />
      <div style="display:flex;justify-content:flex-end;align-items:center;margin-top:8px">
        <el-button
          type="primary"
          size="small"
          :loading="submitting"
          :disabled="submitting || !newComment.trim() || newComment.length > MAX_LENGTH"
          @click="submitComment"
        >{{ $t('todo.comment') }}</el-button>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="comments.length === 0" class="empty">{{ $t('todo.noComment') }}</div>

    <!-- 评论列表 -->
    <CommentItem
      v-for="item in comments"
      :key="item.id"
      :comment="item"
      @delete="handleDelete"
      @reply="handleReply"
    />

    <!-- 回复输入 -->
    <div v-if="replyTo" class="reply-section">
      <MentionInput
        v-model="replyContent"
        :members="members"
        :placeholder="$t('todo.replyMentionPlaceholder')"
        :max-length="MAX_LENGTH"
      />
      <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:8px">
        <el-button size="small" @click="cancelReply">{{ $t('common.cancel') }}</el-button>
        <el-button
          size="small"
          type="primary"
          :loading="submitting"
          :disabled="submitting || !replyContent.trim() || replyContent.length > MAX_LENGTH"
          @click="submitReply"
        >{{ $t('todo.reply') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { Comment, TeamMember } from '@/types'
import { commentsApi } from '@/api/comments'
import CommentItem from './CommentItem.vue'
import MentionInput from './MentionInput.vue'

const { t } = useI18n()
const MAX_LENGTH = 2000

const props = defineProps<{ todoId: number; members?: TeamMember[] }>()

const comments = ref<Comment[]>([])
const newComment = ref('')
const replyTo = ref<number | null>(null)
const replyContent = ref('')
const submitting = ref(false)

async function fetch() {
  const { data } = await commentsApi.list(props.todoId)
  comments.value = data.data
}

async function submitComment() {
  const text = newComment.value.trim()
  if (!text || text.length > MAX_LENGTH || submitting.value) return
  submitting.value = true
  try {
    await commentsApi.create(props.todoId, text)
    newComment.value = ''
    ElMessage.success(t('todo.commentAdded'))
    await fetch()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('todo.commentFailed'))
  } finally {
    submitting.value = false
  }
}

async function submitReply() {
  if (!replyTo.value) return
  const text = replyContent.value.trim()
  if (!text || text.length > MAX_LENGTH || submitting.value) return
  submitting.value = true
  try {
    await commentsApi.create(props.todoId, text, replyTo.value)
    replyContent.value = ''
    replyTo.value = null
    ElMessage.success(t('todo.replySuccess'))
    await fetch()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('todo.replyFailed'))
  } finally {
    submitting.value = false
  }
}

function handleReply(payload: { id: number; displayName: string }) {
  replyTo.value = payload.id
  replyContent.value = `@${payload.displayName} `
}

function cancelReply() {
  replyTo.value = null
  replyContent.value = ''
}

async function handleDelete(commentId: number) {
  try {
    await commentsApi.delete(props.todoId, commentId)
    ElMessage.success(t('todo.commentDeleted'))
    await fetch()
  } catch {
    ElMessage.error(t('todo.commentDeleteFailed'))
  }
}

onMounted(fetch)
</script>

<style scoped>
.comment-list { margin-top: 16px; }
.comment-input { margin-bottom: 16px; }
.reply-section {
  margin-top: 16px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
.empty { color: var(--el-text-color-placeholder); font-size: 13px; padding: 8px 0; }
</style>
