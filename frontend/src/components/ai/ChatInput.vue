<template>
  <div class="chat-input-wrap">
    <!-- Selected references as tags above textarea -->
    <div v-if="references.length > 0" class="reference-tags">
      <el-tag
        v-for="ref in references"
        :key="ref.id"
        closable
        size="small"
        type="info"
        @close="removeReference(ref.id)"
      >
        #{{ ref.id }} {{ truncateText(ref.text) }}
      </el-tag>
    </div>

    <!-- Main input row -->
    <div class="input-row" :class="{ 'is-focused': focused }">
      <!-- Attachment button (UI only, disabled) -->
      <el-button
        class="attach-btn"
        :icon="Paperclip"
        circle
        :disabled="true"
        text
        size="small"
      />

      <!-- Textarea wrapper for relative positioning of popover -->
      <div class="textarea-wrapper">
        <textarea
          ref="textareaRef"
          class="chat-textarea"
          :value="modelValue"
          :placeholder="loading ? 'AI 正在思考...' : '输入你的问题...'"
          :disabled="loading"
          rows="2"
          @input="onInput"
          @keydown="onKeydown"
          @focus="focused = true"
          @blur="onBlur"
        />

        <!-- @ mention search popover floating above the input -->
        <div
          v-if="showMentionDropdown && searchResults.length > 0"
          class="mention-popover"
        >
          <div class="popover-header">选择要引用的任务</div>
          <div
            v-for="(item, index) in searchResults"
            :key="item.id"
            class="mention-item"
            :class="{ selected: index === selectedIdx }"
            @mousedown.prevent="selectReference(item)"
          >
            <span class="mention-id">#{{ item.id }}</span>
            <span class="mention-text">{{ item.text }}</span>
          </div>
        </div>

        <div
          v-if="showMentionDropdown && searchResults.length === 0 && mentionQuery.length >= 1"
          class="mention-popover mention-empty"
        >
          <div class="empty-text">未找到匹配的任务</div>
        </div>
      </div>

      <!-- Stop button (shown when loading, replaces send button) -->
      <el-button
        v-if="loading"
        class="stop-btn"
        type="danger"
        round
        size="small"
        @click="emit('stop')"
      >
        停止生成
      </el-button>

      <!-- Send button (shown when not loading) -->
      <el-button
        v-else
        class="send-btn"
        type="primary"
        :icon="ArrowUp"
        circle
        :disabled="!modelValue.trim() && references.length === 0"
        @click="handleSend"
      />
    </div>

    <!-- Input hints bar -->
    <div class="input-hints">
      Enter 发送 · Shift+Enter 换行 · @ 引用任务
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { ArrowUp, Paperclip } from '@element-plus/icons-vue'
import http from '@/api/http'
import type { Todo } from '@/types'

// --- Reference type ---
interface Reference {
  id: number
  text: string
}

// --- Props ---
const props = withDefaults(defineProps<{
  loading: boolean
  modelValue: string
}>(), {
  loading: false,
  modelValue: ''
})

// --- Emits ---
const emit = defineEmits<{
  send: [text: string, references: Reference[]]
  stop: []
  'update:modelValue': [value: string]
}>()

// --- Refs ---
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const focused = ref(false)
const references = ref<Reference[]>([])

// --- Mention / @ search state ---
const showMentionDropdown = ref(false)
const mentionQuery = ref('')
const searchResults = ref<Todo[]>([])
const selectedIdx = ref(0)
let mentionStart = -1 // cursor position of the '@' character in the text
let searchTimer: ReturnType<typeof setTimeout> | null = null

// --- Cleanup ---
onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
})

// --- Auto-height adjustment ---
function adjustHeight(ta: HTMLTextAreaElement) {
  // Reset to auto so scrollHeight is accurate for a shrink
  ta.style.height = 'auto'
  const styles = window.getComputedStyle(ta)
  const lineHeight = parseFloat(styles.lineHeight) || 22
  const paddingTop = parseFloat(styles.paddingTop) || 0
  const paddingBottom = parseFloat(styles.paddingBottom) || 0
  const borderTop = parseFloat(styles.borderTopWidth) || 0
  const borderBottom = parseFloat(styles.borderBottomWidth) || 0
  const verticalExtra = paddingTop + paddingBottom + borderTop + borderBottom

  const minRows = 2
  const maxRows = 6
  const minHeight = lineHeight * minRows + verticalExtra
  const maxHeight = lineHeight * maxRows + verticalExtra

  ta.style.height = Math.min(Math.max(ta.scrollHeight, minHeight), maxHeight) + 'px'
  ta.style.overflowY = ta.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

// --- Text input ---
function onInput(e: Event) {
  const ta = e.target as HTMLTextAreaElement
  const val = ta.value
  emit('update:modelValue', val)

  // Auto-height
  adjustHeight(ta)

  // Check for @ mention trigger
  const cursor = ta.selectionStart
  const beforeCursor = val.slice(0, cursor)
  const atIdx = beforeCursor.lastIndexOf('@')

  if (atIdx >= 0) {
    // Valid @ position: at start, after space, or after newline
    const charBeforeAt = atIdx > 0 ? beforeCursor[atIdx - 1] : ' '
    const isValidStart = charBeforeAt === ' ' || charBeforeAt === '\n' || atIdx === 0
    if (isValidStart) {
      const query = val.slice(atIdx + 1, cursor)
      // Only trigger if query has no spaces/newlines
      if (!query.includes(' ') && !query.includes('\n')) {
        mentionStart = atIdx
        mentionQuery.value = query
        showMentionDropdown.value = true
        selectedIdx.value = 0
        debounceSearch(query)
        return
      }
    }
  }

  // No valid @ mention
  showMentionDropdown.value = false
  mentionStart = -1
  mentionQuery.value = ''
}

// --- Debounced search ---
function debounceSearch(query: string) {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    doSearch(query)
  }, 250)
}

async function doSearch(query: string) {
  if (query.length === 0) {
    searchResults.value = []
    return
  }
  try {
    const res = await http.get<{ data: Todo[] }>('/todos', {
      params: { q: query, status: '', pageSize: 5 }
    })
    searchResults.value = res.data?.data ?? []
    selectedIdx.value = 0
  } catch {
    searchResults.value = []
  }
}

// --- Select a reference from the dropdown ---
function selectReference(todo: Todo) {
  const ta = textareaRef.value
  if (!ta) return

  const val = ta.value
  const before = val.slice(0, mentionStart)
  const afterCursor = val.slice(mentionStart + 1 + mentionQuery.value.length)
  const newVal = before + afterCursor
  ta.value = newVal
  emit('update:modelValue', newVal)

  // Check for duplicate
  if (!references.value.some(r => r.id === todo.id)) {
    references.value.push({ id: todo.id, text: todo.text })
  }

  showMentionDropdown.value = false
  mentionStart = -1
  mentionQuery.value = ''
  searchResults.value = []

  // Restore cursor position right after where the @query was removed
  const newCursorPos = before.length
  ta.setSelectionRange(newCursorPos, newCursorPos)
  ta.focus()
}

// --- Remove a reference chip ---
function removeReference(id: number) {
  references.value = references.value.filter(r => r.id !== id)
}

// --- Keyboard handling ---
function onKeydown(e: KeyboardEvent) {
  // If dropdown is visible, handle navigation keys
  if (showMentionDropdown.value && searchResults.value.length > 0) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      selectedIdx.value = Math.min(selectedIdx.value + 1, searchResults.value.length - 1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      selectedIdx.value = Math.max(selectedIdx.value - 1, 0)
      return
    }
    if (e.key === 'Enter' || e.key === 'Tab') {
      const selected = searchResults.value[selectedIdx.value]
      if (selected) {
        e.preventDefault()
        selectReference(selected)
      }
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      showMentionDropdown.value = false
      mentionStart = -1
      mentionQuery.value = ''
      searchResults.value = []
      return
    }
  }

  // Send on Enter (without Shift)
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

// --- Blur handler: close dropdown after a small delay to allow mousedown ---
function onBlur() {
  focused.value = false
  setTimeout(() => {
    if (showMentionDropdown.value) {
      showMentionDropdown.value = false
      mentionStart = -1
      mentionQuery.value = ''
      searchResults.value = []
    }
  }, 200)
}

// --- Send message ---
function handleSend() {
  const text = props.modelValue.trim()
  if (!text && references.value.length === 0) return
  if (props.loading) return

  const refs = [...references.value]
  emit('send', text, refs)

  // Clear references after send
  references.value = []
}

// --- Helpers ---
function truncateText(text: string, maxLen = 20): string {
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
}
</script>

<style scoped>
/* ---- Wrapper ---- */
.chat-input-wrap {
  border-top: 1px solid var(--el-border-color-lighter, #e5e7eb);
  padding: 12px 16px;
  background: var(--el-bg-color, #fff);
}

/* ---- Reference tags ---- */
.reference-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.reference-tags :deep(.el-tag) {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- Input row ---- */
.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 10px;
  padding: 6px 10px;
  background: var(--el-fill-color-blank, #fff);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-row.is-focused {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 1px rgba(139, 92, 246, 0.25), 0 0 0 4px rgba(139, 92, 246, 0.1);
}

/* ---- Attachment button ---- */
.attach-btn {
  flex-shrink: 0;
  color: var(--el-text-color-placeholder, #c0c4cc);
  padding: 4px;
}

/* ---- Textarea wrapper (relative for popover) ---- */
.textarea-wrapper {
  flex: 1;
  position: relative;
  min-width: 0;
}

/* ---- Textarea ---- */
.chat-textarea {
  display: block;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.6;
  color: var(--el-text-color-primary, #303133);
  background: transparent;
  padding: 4px 0;
  min-height: 52px; /* ~2 rows at 14px * 1.6 line-height + padding */
  overflow-y: hidden;
}

.chat-textarea::placeholder {
  color: var(--el-text-color-placeholder, #c0c4cc);
}

.chat-textarea:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

/* ---- @ Mention popover (floating above textarea) ---- */
.mention-popover {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  z-index: 3000;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  min-width: 240px;
  max-height: 260px;
  overflow-y: auto;
}

.popover-header {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.mention-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.mention-item:hover,
.mention-item.selected {
  background: var(--el-color-primary-light-9, #ecf5ff);
}

.mention-id {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary, #409eff);
  font-family: 'SF Mono', 'Cascadia Code', monospace;
}

.mention-text {
  flex: 1;
  font-size: 13px;
  color: var(--el-text-color-primary, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Empty state for mention popover */
.mention-empty {
  padding: 16px;
  text-align: center;
}

.mention-empty .empty-text {
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
}

/* ---- Stop button ---- */
.stop-btn {
  flex-shrink: 0;
  height: 32px;
  font-size: 13px;
}

/* ---- Send button ---- */
.send-btn {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  padding: 0;
}

.send-btn :deep(.el-icon) {
  font-size: 18px;
}

/* ---- Hints bar ---- */
.input-hints {
  margin-top: 6px;
  text-align: right;
  font-size: 12px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  user-select: none;
  line-height: 1.4;
}
</style>
