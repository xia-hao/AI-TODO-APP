<template>
  <div class="message-row" :class="role">
    <div class="avatar">{{ role === 'assistant' ? 'AI' : userName }}</div>
    <div class="message-content">
      <!-- 预加载状态：等待第一个 token -->
      <div v-if="loading && !content" class="bubble loading-bubble">
        <span v-if="thinkingHint" class="thinking-hint">{{ thinkingHint }}</span>
        <template v-else>
          <span class="dot" /><span class="dot" /><span class="dot" />
        </template>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error && !content" class="bubble error-bubble">
        <span class="error-icon">⚠️</span><span>{{ content || '请求失败' }}</span>
        <el-button v-if="!content" text size="small" class="retry-btn" @click="$emit('retry')">重试</el-button>
      </div>

      <!-- 正常内容区域（流式 + tool call thinking 都在这里） -->
      <template v-else-if="content">
        <!-- Tool call 进行中的提示条 -->
        <div v-if="thinkingHint" class="thinking-badge">{{ thinkingHint }}</div>
        <div class="bubble">
          <div class="markdown-body" v-html="renderedContent" />
        </div>
        <div class="message-actions">
          <el-tooltip content="复制" placement="top">
            <el-button text size="small" class="action-btn" @click="handleCopy">{{ copied ? '已复制' : '📋' }}</el-button>
          </el-tooltip>
          <el-tooltip content="重新生成" placement="top">
            <el-button text size="small" class="action-btn" @click="$emit('regenerate')">🔄</el-button>
          </el-tooltip>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ role: 'user' | 'assistant'; content: string; loading?: boolean; thinkingHint?: string; error?: boolean }>()
defineEmits<{ retry: []; regenerate: [] }>()
const auth = useAuthStore()
const userName = computed(() => auth.user?.displayName?.charAt(0) || '我')
const copied = ref(false)

// 打字机：逐字 reveal
const displayedContent = ref('')
let revealTimer: ReturnType<typeof setTimeout> | null = null
let typingStarted = false  // true 表示正在打字中，加载结束时不要 revealAll

function scheduleReveal() {
  if (revealTimer) return
  revealTimer = setTimeout(() => {
    revealTimer = null
    const full = props.content
    if (displayedContent.value.length < full.length) {
      displayedContent.value += full[displayedContent.value.length]
      scheduleReveal()
    }
  }, 50)
}

function revealAll() {
  if (revealTimer) {
    clearTimeout(revealTimer)
    revealTimer = null
  }
  displayedContent.value = props.content
}

watch(() => props.content, (newVal, oldVal) => {
  if (!newVal) { displayedContent.value = ''; return }
  // 历史消息（从未进入打字模式）：立即全 reveal
  if (!props.loading && !typingStarted) { revealAll(); return }
  // 首次有内容：进入打字模式
  if (!oldVal) {
    typingStarted = true
    displayedContent.value = newVal[0] || ''
    scheduleReveal()
    return
  }
  // 内容继续增长：让定时器继续消费
  if (!revealTimer && displayedContent.value.length < newVal.length) {
    scheduleReveal()
  }
}, { immediate: true })

onUnmounted(() => {
  if (revealTimer) clearTimeout(revealTimer)
})

// 只渲染已 reveal 的部分
const renderedContent = computed(() => {
  if (!displayedContent.value) return ''
  const rawHtml = marked.parse(displayedContent.value, { breaks: true }) as string
  return DOMPurify.sanitize(rawHtml, { ADD_TAGS: ['pre', 'code', 'div', 'span'], ADD_ATTR: ['class'] })
})

async function handleCopy() {
  try { await navigator.clipboard.writeText(props.content) } catch {
    const ta = document.createElement('textarea')
    ta.value = props.content; document.body.appendChild(ta); ta.select()
    document.execCommand('copy'); document.body.removeChild(ta)
  }
  copied.value = true; setTimeout(() => { copied.value = false }, 2000)
}
</script>

<style scoped>
.message-row { display: flex; gap: 10px; max-width: 100%; }
.message-row.user { flex-direction: row-reverse; }
.avatar { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; flex-shrink: 0; }
.message-row.assistant .avatar { background: linear-gradient(135deg, var(--el-color-primary), #8b5cf6); color: #fff; }
.message-row.user .avatar { background: var(--el-color-primary); color: #fff; }
.message-content { flex: 1; min-width: 0; max-width: 80%; }
.message-row.user .message-content { display: flex; flex-direction: column; align-items: flex-end; }
.bubble { padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.7; word-break: break-word; }
.message-row.assistant .bubble { background: var(--el-fill-color-light); color: var(--el-text-color-primary); border-bottom-left-radius: 4px; }
.message-row.user .bubble { background: var(--el-color-primary); color: #fff; border-bottom-right-radius: 4px; }
.bubble { user-select: text !important; -webkit-user-select: text !important; }
.bubble :deep(*) { user-select: text !important; -webkit-user-select: text !important; }

/** Loading / Waiting dots */
.loading-bubble { display: flex; gap: 4px; align-items: center; padding: 14px 18px !important; min-height: 36px; }
.thinking-hint { font-size: 13px; color: var(--el-text-color-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 200px; }
.thinking-hint::after { content: ''; display: inline-block; width: 6px; height: 6px; margin-left: 6px; background: var(--el-color-primary); border-radius: 50%; animation: thinking-pulse 1s ease-in-out infinite; vertical-align: middle; }
@keyframes thinking-pulse { 0%,100% { opacity: 0.3; } 50% { opacity: 1; } }
.dot { width: 8px; height: 8px; background: var(--el-text-color-secondary); animation: bounce 1.4s infinite ease-in-out both; border-radius: 50%; }
.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }
.dot:nth-child(3) { animation-delay: 0s; }
@keyframes bounce { 0%,80%,100% { transform: scale(0); } 40% { transform: scale(1); } }

/** Error */
.error-bubble { background: var(--el-color-danger-light-9) !important; color: var(--el-color-danger) !important; display: flex; align-items: center; gap: 8px; }
.error-icon { font-size: 16px; }
.retry-btn { color: var(--el-color-primary); font-size: 12px; }

/** Tool call thinking badge - 出现在已有内容上方 */
.thinking-badge { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--el-color-primary); margin-bottom: 6px; padding: 3px 10px; background: var(--el-color-primary-light-9); border-radius: 10px; }
.thinking-badge::before { content: ''; width: 6px; height: 6px; background: var(--el-color-primary); border-radius: 50%; animation: thinking-pulse 1s ease-in-out infinite; }

/** Message actions */
.message-actions { display: flex; gap: 2px; margin-top: 4px; opacity: 0; transition: opacity 0.15s; padding-left: 4px; }
.message-row.assistant .message-content:hover .message-actions { opacity: 1; }
.action-btn { font-size: 14px; height: 28px; padding: 0 6px; color: var(--el-text-color-secondary); }
.action-btn.active { color: var(--el-color-primary); }

/** Markdown */
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(code) { background: var(--el-fill-color-darker); padding: 2px 6px; border-radius: 4px; font-size: 13px; font-family: 'Menlo', 'Monaco', 'Courier New', monospace; }
.markdown-body :deep(pre) { margin: 8px 0; border-radius: 10px; overflow: hidden; }
.markdown-body :deep(pre code) { background: #1e1e1e; color: #d4d4d4; display: block; padding: 14px 16px; overflow-x: auto; font-size: 13px; line-height: 1.6; }
</style>
