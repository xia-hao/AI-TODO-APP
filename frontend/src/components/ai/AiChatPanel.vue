<template>
  <div class="ai-chat-panel">
    <ConversationSidebar
      :conversations="conversations"
      :current-id="currentId"
      :loading="loading"
      :conversations-loading="conversationsLoading"
      @create="aiStore.newConversation()"
      @select="aiStore.selectConversation($event)"
      @delete="aiStore.removeConversation($event)"
      @rename="aiStore.renameConversationAction($event.id, $event.title)"
    />

    <ChatView
      :messages="messages"
      :loading="loading"
      :conversations-loading="conversationsLoading"
      :messages-loading="messagesLoading"
      :error="error"
      :thinking-hint="aiStore.thinkingHint"
      :draft-text="draftText"
      @send="handleSend"
      @stop="handleStop"
      @regenerate="handleRegenerate"
      @retry="handleRetry"
      @update:draft-text="handleDraftChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAiStore } from '@/stores/ai'
import ConversationSidebar from './ConversationList.vue'
import ChatView from './ChatView.vue'

const aiStore = useAiStore()
const { conversations, currentId, messages, loading, conversationsLoading, messagesLoading, error } = storeToRefs(aiStore)
const draftText = ref('')

onMounted(() => {
  if (conversations.value.length === 0) {
    aiStore.fetchConversations()
  }
})

// Save/restore drafts when switching conversations
watch(currentId, (newId, oldId) => {
  if (oldId != null && draftText.value) {
    aiStore.saveDraft(draftText.value)
  }
  draftText.value = newId != null ? aiStore.getDraft() : ''
})

function handleSend(text: string) {
  aiStore.sendMessage(text)
  draftText.value = ''
}

function handleStop() {
  aiStore.stopGeneration()
}

function handleRegenerate() {
  aiStore.regenerate()
}

function handleRetry() {
  aiStore.regenerate()
}

function handleDraftChange(val: string) {
  draftText.value = val
}
</script>

<style scoped>
.ai-chat-panel {
  display: flex;
  height: 100%;
}
</style>
