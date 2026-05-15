import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  chatStream, listConversations,
  createConversation, deleteConversation, renameConversation,
  getConversationMessages
} from '@/api/ai'
import type { Conversation } from '@/api/ai'
import { ElMessage, ElMessageBox } from 'element-plus'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  id: string
}

export const useAiStore = defineStore('ai', () => {
  const conversations = ref<Conversation[]>([])
  const currentId = ref<number | null>(null)
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)
  const conversationsLoading = ref(false)
  const messagesLoading = ref(false)
  const error = ref('')
  const showPanel = ref(false)
  const thinkingHint = ref('')
  const abortController = ref<AbortController | null>(null)
  const draftMessages = ref<Record<number, string>>({})

  const currentConversation = computed(() =>
    conversations.value.find(c => c.id === currentId.value) || null
  )

  async function fetchConversations() {
    conversationsLoading.value = true
    try {
      const res = await listConversations()
      conversations.value = res.data.data || []
      // Auto-select first if none selected
      if (!currentId.value && conversations.value.length > 0) {
        await selectConversation(conversations.value[0].id)
      }
    } catch {
      ElMessage.error('加载会话列表失败')
    } finally {
      conversationsLoading.value = false
    }
  }

  async function selectConversation(id: number) {
    currentId.value = id
    messages.value = []
    messagesLoading.value = true
    try {
      const res = await getConversationMessages(id)
      const data = res.data.data || []
      messages.value = data.map((m: any, i: number) => ({
        role: m.role,
        content: m.content || '',
        id: `msg-${id}-${i}`,
      }))
    } catch {
      ElMessage.error('加载历史消息失败')
    } finally {
      messagesLoading.value = false
    }
  }

  async function newConversation() {
    try {
      const res = await createConversation()
      const conv = res.data.data
      conversations.value.unshift(conv)
      currentId.value = conv.id
      messages.value = []
    } catch {
      ElMessage.error('创建会话失败')
    }
  }

  async function removeConversation(id: number) {
    try {
      await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      })
      await deleteConversation(id)
      conversations.value = conversations.value.filter(c => c.id !== id)
      if (currentId.value === id) {
        currentId.value = null
        messages.value = []
        if (conversations.value.length > 0) {
          await selectConversation(conversations.value[0].id)
        }
      }
    } catch {
      // cancelled or error
    }
  }

  async function renameConversationAction(id: number, title: string) {
    try {
      await renameConversation(id, title)
      const conv = conversations.value.find(c => c.id === id)
      if (conv) conv.title = title
    } catch {
      ElMessage.error('重命名失败')
    }
  }

  async function sendMessage(text: string) {
    if (!text.trim() || loading.value) return

    // Auto-create conversation if none selected
    if (!currentId.value) {
      await newConversation()
      // After creating, if still null, abort
      if (!currentId.value) return
    }

    const userMsg: ChatMessage = {
      role: 'user', content: text, id: Date.now().toString(),
    }
    messages.value.push(userMsg)
    loading.value = true
    error.value = ''

    const assistantMsg: ChatMessage = {
      role: 'assistant', content: '', id: (Date.now() + 1).toString(),
    }
    messages.value.push(assistantMsg)

    // Create AbortController for this request
    const controller = new AbortController()
    abortController.value = controller

    const convId = currentId.value!

    try {
      const stream = chatStream(
        {
          message: text,
          sessionId: String(convId),
          conversationId: convId,
        },
        controller.signal,
      )

      await stream.start(
        (data: any) => {
          if (data.type === 'text' || data.type === 'token') {
            assistantMsg.content += data.content || ''
          } else if (data.type === 'thinking') {
            thinkingHint.value = data.hint || '处理中...'
          }
        },
        () => {
          loading.value = false
          thinkingHint.value = ''
          abortController.value = null
          if (!assistantMsg.content) {
            assistantMsg.content = 'AI 服务暂不可用，请稍后重试。'
          }
          // Refresh to pick up auto-generated title
          fetchConversations()
        },
        (err: any) => {
          loading.value = false
          thinkingHint.value = ''
          abortController.value = null
          error.value = err.message || '请求失败'
          assistantMsg.content = err.message || '抱歉，我遇到了问题，请稍后再试。'
        }
      )
    } catch (err: any) {
      loading.value = false
      thinkingHint.value = ''
      abortController.value = null
      error.value = err.message || '请求失败'
      assistantMsg.content = err.message || '抱歉，我遇到了问题，请稍后再试。'
    }
  }

  function stopGeneration() {
    if (!loading.value) return
    abortController.value?.abort()
    // Remove the last empty assistant message that was being streamed into
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant' && last.content === '') {
      messages.value.pop()
    }
    loading.value = false
    abortController.value = null
  }

  function regenerate() {
    if (loading.value) return
    // Find the last user message
    const lastUserIdx = messages.value.map(m => m.role).lastIndexOf('user')
    if (lastUserIdx === -1) return

    const lastUserMsg = messages.value[lastUserIdx]
    // Remove everything from the assistant message after that user message onward
    // (the assistant message follows the user message immediately)
    if (lastUserIdx + 1 < messages.value.length) {
      messages.value.splice(lastUserIdx + 1)
    }
    // Re-send the user's last message
    sendMessage(lastUserMsg.content)
  }

  function saveDraft(text: string) {
    if (currentId.value !== null) {
      draftMessages.value = {
        ...draftMessages.value,
        [currentId.value]: text,
      }
    }
  }

  function getDraft(): string {
    if (currentId.value === null) return ''
    return draftMessages.value[currentId.value] || ''
  }

  function clearDraft() {
    if (currentId.value !== null) {
      const { [currentId.value]: _, ...rest } = draftMessages.value
      draftMessages.value = rest
    }
  }

  function togglePanel() {
    showPanel.value = !showPanel.value
    if (showPanel.value && conversations.value.length === 0) {
      fetchConversations()
    }
  }

  return {
    conversations, currentId, messages, loading, error, showPanel, thinkingHint,
    conversationsLoading, messagesLoading, abortController, draftMessages,
    currentConversation,
    fetchConversations, selectConversation, newConversation,
    removeConversation, renameConversationAction, sendMessage,
    stopGeneration, regenerate, saveDraft, getDraft, clearDraft,
    togglePanel,
  }
})
