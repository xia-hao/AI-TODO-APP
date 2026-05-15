<template>
  <div class="chat-view">
    <div class="chat-header">
      <span class="chat-title">AI 助手</span>
    </div>

    <div class="chat-messages" ref="messagesRef">
      <div v-if="(conversationsLoading || messagesLoading) && messages.length === 0" class="loading-container">
        <el-skeleton animated :rows="1" class="chat-skeleton" />
      </div>
      <WelcomeScreen
        v-else-if="messages.length === 0 && !loading && !messagesLoading"
        @select="handleWelcomeSelect"
      />
      <template v-else>
        <MessageBubble
          v-for="(msg, index) in messages"
          :key="msg.id"
          :role="asRole(msg.role)"
          :content="msg.content"
          :loading="isLastAssistantMessage(index) && loading"
          :thinking-hint="isLastAssistantMessage(index) ? thinkingHint : ''"
          :error="isLastAssistantMessage(index) && !!error"
          @retry="onRetry"
          @regenerate="onRegenerate"
        />
      </template>
    </div>

    <div class="chat-input-area">
      <ChatInput
        :loading="loading"
        :model-value="draftText"
        @send="onSend"
        @stop="onStop"
        @update:model-value="onDraftUpdate"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import WelcomeScreen from './WelcomeScreen.vue'
import MessageBubble from './MessageBubble.vue'
import ChatInput from './ChatInput.vue'

const props = defineProps<{
  messages: Array<{ id: string; role: string; content: string }>
  loading: boolean
  conversationsLoading: boolean
  messagesLoading: boolean
  error: string
  thinkingHint: string
  draftText: string
}>()

const emit = defineEmits<{
  send: [text: string]
  stop: []
  regenerate: []
  retry: []
  'update:draftText': [value: string]
}>()

const messagesRef = ref<HTMLElement | null>(null)

function asRole(r: string): 'user' | 'assistant' {
  return r as 'user' | 'assistant'
}

function isLastAssistantMessage(index: number): boolean {
  const msg = props.messages[index]
  if (!msg || msg.role !== 'assistant') return false
  // Check no later assistant messages exist
  for (let i = index + 1; i < props.messages.length; i++) {
    if (props.messages[i].role === 'assistant') return false
  }
  return true
}

function handleWelcomeSelect(text: string) {
  emit('send', text)
}

function onSend(text: string) {
  emit('send', text)
}

function onStop() {
  emit('stop')
}

function onRetry() {
  emit('retry')
}

function onRegenerate() {
  emit('regenerate')
}

function onDraftUpdate(value: string) {
  emit('update:draftText', value)
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// Auto-scroll when messages change or loading state changes
watch(() => props.messages.length, () => scrollToBottom())
watch(() => props.loading, () => scrollToBottom())
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  height: 100%;
  background: var(--el-bg-color);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.loading-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.chat-skeleton {
  width: 100%;
  max-width: 400px;
}

.chat-input-area {
  flex-shrink: 0;
  padding: 12px 16px;
  border-top: 1px solid var(--el-border-color-light);
}
</style>
