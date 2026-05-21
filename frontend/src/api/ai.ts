import http from './http'
import { useAuthStore } from '@/stores/auth'

export interface AiChatRequest {
  message: string
  sessionId?: string
  conversationId?: number
}

export interface StreamController {
  start: (onMessage: (data: any) => void, onDone: () => void, onError: (err: any) => void) => Promise<void>
  abort: () => void
}

export interface Conversation {
  id: number
  title: string
  updateTime: string
}

export function listConversations() {
  return http.get<{ success: boolean; data: Conversation[] }>('/ai/conversations')
}

export function createConversation() {
  return http.post<{ success: boolean; data: Conversation }>('/ai/conversations')
}

export function deleteConversation(id: number) {
  return http.delete(`/ai/conversations/${id}`)
}

export function renameConversation(id: number, title: string) {
  return http.put(`/ai/conversations/${id}/rename`, { title })
}

export function getConversationMessages(id: number) {
  return http.get<{ success: boolean; data: any[] }>(`/ai/conversations/${id}/messages`)
}

export function confirmAction(confirmId: string, approved: boolean) {
  return http.post('/ai/chat/confirm-action', { confirmId, approved })
}

export function chatStream(request: AiChatRequest, externalSignal?: AbortSignal): StreamController {
  const internalController = externalSignal ? null : new AbortController()
  const signal = externalSignal || internalController!.signal

  return {
    async start(onMessage, onDone, onError) {
      try {
        const token = await useAuthStore().getFreshToken()
        const response = await fetch('/api/ai/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify(request),
          signal,
        })
        if (!response.ok) {
          onError(new Error(`HTTP ${response.status}`))
          return
        }

        const reader = response.body?.getReader()
        if (!reader) {
          onError(new Error('No response body'))
          return
        }

        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            const trimmed = line.trim()
            if (!trimmed) continue

            // data: 可能不带空格（Spring SseEmitter 输出格式），需兼容两种
            if (trimmed.startsWith('data:')) {
              const data = trimmed.slice(5).trim()
              try {
                const parsed = JSON.parse(data)

                if (parsed.type === 'done') {
                  onDone()
                  return
                }
                if (parsed.type === 'error') {
                  onError(new Error(parsed.content || '服务异常'))
                  return
                }
                onMessage(parsed)
              } catch {
                // ignore partial data
              }
            }
          }
        }
        onDone()
      } catch (err: any) {
        if (err.name !== 'AbortError') {
          onError(err)
        }
      }
    },
    abort() {
      if (internalController) {
        internalController.abort()
      }
    },
  }
}
