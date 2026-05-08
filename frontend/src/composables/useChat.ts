import { ref, onUnmounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import type { ThinkingEventData, TokenEventData, DoneEventData, ErrorEventData } from '@/types/chat'

const MAX_RETRIES = 3
const RETRY_DELAY_MS = 1000

export function useChat() {
  const chatStore = useChatStore()
  const abortController = ref<AbortController | null>(null)
  const retryCount = ref(0)

  const sendMessage = async (question: string, sessionId: string) => {
    const ac = new AbortController()
    abortController.value = ac
    retryCount.value = 0

    // 重置上一轮思考过程
    chatStore.activeThinking = null

    const convId = chatStore.activeConversationId ?? chatStore.createConversation()

    // 1. Add user message
    chatStore.addMessage(convId, { role: 'user', content: question })

    // 2. Create empty assistant message placeholder
    const assistantMsgId = chatStore.addMessage(convId, {
      role: 'assistant',
      content: '',
      isStreaming: true,
    })
    chatStore.connectionStatus = 'connecting'

    await connectStream(question, sessionId, convId, assistantMsgId, ac)
  }

  const connectStream = async (
    question: string,
    sessionId: string,
    convId: string,
    assistantMsgId: string,
    ac: AbortController,
  ): Promise<void> => {
    try {
      const authStore = useAuthStore()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      }
      if (authStore.token) {
        headers['Authorization'] = `Bearer ${authStore.token}`
      }
      const response = await fetch('/api/rag/ask/stream', {
        method: 'POST',
        headers,
        body: JSON.stringify({ question, sessionId, stream: true }),
        signal: ac.signal,
      })

      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      chatStore.connectionStatus = 'connected'
      retryCount.value = 0 // Reset on successful connection

      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (trimmed === '') {
            currentEvent = ''
            continue
          }
          if (trimmed.startsWith('event:')) {
            currentEvent = trimmed.slice(6).trim()
          } else if (trimmed.startsWith('data:')) {
            try {
              const data = JSON.parse(trimmed.slice(5).trim())
              processEvent(currentEvent, data, convId, assistantMsgId)
            } catch {
              // malformed JSON — skip
            }
          }
        }
      }
    } catch (err: any) {
      if (err.name === 'AbortError') {
        chatStore.appendToken(convId, assistantMsgId, '\n\n*[已停止生成]*')
      } else {
        // Attempt retry
        if (retryCount.value < MAX_RETRIES) {
          retryCount.value++
          chatStore.connectionStatus = 'connecting'
          await new Promise(resolve => setTimeout(resolve, RETRY_DELAY_MS))
          await connectStream(question, sessionId, convId, assistantMsgId, ac)
          return
        }
        // All retries exhausted
        chatStore.connectionStatus = 'error'
        chatStore.appendToken(convId, assistantMsgId, '\n\n*[连接异常，已重试3次仍失败，请稍后重试]*')
      }
    } finally {
      chatStore.clearThinking()
      chatStore.connectionStatus = 'idle'
    }
  }

  const processEvent = (
    event: string,
    data: ThinkingEventData | TokenEventData | DoneEventData | ErrorEventData,
    convId: string,
    msgId: string,
  ) => {
    switch (event) {
      case 'thinking': {
        const t = data as ThinkingEventData
        chatStore.updateThinking(t)
        break
      }
      case 'token': {
        const t = data as TokenEventData
        chatStore.appendToken(convId, msgId, t.token)
        break
      }
      case 'done': {
        const d = data as DoneEventData
        chatStore.finalizeMessage(convId, msgId, d.citations ?? [])
        break
      }
      case 'error': {
        const e = data as ErrorEventData
        chatStore.appendToken(convId, msgId, `\n\n*[${e.error}]*`)
        chatStore.connectionStatus = 'error'
        break
      }
    }
  }

  const stopGeneration = () => {
    abortController.value?.abort()
  }

  onUnmounted(() => {
    abortController.value?.abort()
  })

  return { sendMessage, stopGeneration }
}
