import { defineStore } from 'pinia'
import { ref, computed, reactive } from 'vue'
import type { Message, Conversation, Citation, ThinkingStep, ConnectionStatus } from '@/types/chat'
import { getConversations, getConversationDetail, deleteConversation as deleteConvApi } from '@/api/rag'

export const useChatStore = defineStore('chat', () => {
  // ── State ──
  const conversations = ref<Conversation[]>([])
  const activeConversationId = ref<string | null>(null)
  const conversationsLoading = ref(false)

  const messages = ref<Record<string, Message[]>>({})
  const streamingMessageId = ref<string | null>(null)
  const connectionStatus = ref<ConnectionStatus>('idle')

  const activeThinking = ref<{
    steps: ThinkingStep[]
    currentStage: string | null
  } | null>(null)

  const citationPanel = reactive({
    isOpen: false,
    citations: [] as Citation[],
    activeIndex: null as number | null,
  })

  // ── Getters ──
  const currentMessages = computed(() =>
    activeConversationId.value ? (messages.value[activeConversationId.value] ?? []) : []
  )

  const isStreaming = computed(() => streamingMessageId.value !== null)

  // ── Actions ──

  function createConversation(): string {
    const id = crypto.randomUUID()
    conversations.value.unshift({
      id,
      title: '新对话',
      lastMessage: '',
      updatedAt: new Date().toISOString(),
    })
    messages.value[id] = []
    activeConversationId.value = id
    return id
  }

  function setActiveConversation(id: string) {
    activeConversationId.value = id
    if (!messages.value[id]) {
      fetchConversationDetail(id)
    }
  }

  async function fetchConversationDetail(id: string) {
    try {
      const res = await getConversationDetail(id)
      if (res.success && res.history) {
        const msgs: Message[] = res.history.map((entry, i) => ({
          id: id + '-' + i,
          role: entry.role as 'user' | 'assistant',
          content: entry.content,
        }))
        messages.value[id] = msgs
      }
    } catch {
      // Not found — conversation has no messages yet
      messages.value[id] = []
    }
  }

  function addMessage(convId: string, msg: Omit<Message, 'id' | 'createdAt'>): string {
    const msgId = crypto.randomUUID()
    const entry: Message = { ...msg, id: msgId, createdAt: new Date().toISOString() }
    if (!messages.value[convId]) messages.value[convId] = []
    messages.value[convId].push(entry)
    return msgId
  }

  function appendToken(convId: string, msgId: string, token: string) {
    const msgs = messages.value[convId]
    if (!msgs) return
    const target = msgs.find(m => m.id === msgId)
    if (target) target.content += token
  }

  function finalizeMessage(convId: string, msgId: string, citations: Citation[]) {
    const msgs = messages.value[convId]
    if (!msgs) return
    const target = msgs.find(m => m.id === msgId)
    if (target) {
      target.isStreaming = false
      target.citations = citations
      target.safetyStatus = 'pass'
      // 持久化思考步骤到消息（深拷贝避免后续轮次污染）
      if (activeThinking.value) {
        target.thinking = {
          steps: activeThinking.value.steps.map(s => ({ ...s })),
          currentStage: null,
        }
      }
    }
    streamingMessageId.value = null

    // Update conversation title from first user message
    const conv = conversations.value.find(c => c.id === convId)
    if (conv) {
      const firstUser = msgs.find(m => m.role === 'user')
      if (firstUser) {
        conv.title = firstUser.content.slice(0, 30) + (firstUser.content.length > 30 ? '...' : '')
        conv.lastMessage = target?.content.slice(0, 80) ?? ''
      }
      conv.updatedAt = new Date().toISOString()
    }
  }

  function updateThinking(data: ThinkingStep) {
    if (!activeThinking.value) {
      activeThinking.value = { steps: [], currentStage: null }
    }
    const existing = activeThinking.value.steps.find(s => s.stage === data.stage)
    if (existing) {
      Object.assign(existing, data)
    } else {
      activeThinking.value.steps.push({ ...data })
    }
    activeThinking.value.currentStage = data.stage
  }

  function clearThinking() {
    if (!activeThinking.value) return
    activeThinking.value = { steps: activeThinking.value.steps, currentStage: null }
  }

  function openCitation(citations: Citation[], activeIndex?: number) {
    citationPanel.isOpen = true
    citationPanel.citations = citations
    citationPanel.activeIndex = activeIndex ?? null
  }

  function closeCitation() {
    citationPanel.isOpen = false
    citationPanel.activeIndex = null
  }

  async function deleteConversation(id: string) {
    try {
      await deleteConvApi(id)
    } catch { /* server error — clean local state anyway */ }
    conversations.value = conversations.value.filter(c => c.id !== id)
    delete messages.value[id]
    if (activeConversationId.value === id) {
      activeConversationId.value = conversations.value[0]?.id ?? null
    }
  }

  async function fetchConversations() {
    conversationsLoading.value = true
    try {
      const res = await getConversations()
      if (res.success && res.sessions) {
        conversations.value = res.sessions.map(s => ({
          id: s.sessionId,
          title: s.preview ? s.preview.slice(0, 30) : '新对话',
          lastMessage: '',
          updatedAt: new Date().toISOString(),
          rounds: s.rounds,
          preview: s.preview,
          lastRole: s.lastRole,
        }))
      }
    } finally {
      conversationsLoading.value = false
    }
  }

  return {
    conversations,
    activeConversationId,
    conversationsLoading,
    messages,
    streamingMessageId,
    connectionStatus,
    activeThinking,
    citationPanel,
    currentMessages,
    isStreaming,
    createConversation,
    setActiveConversation,
    fetchConversationDetail,
    addMessage,
    appendToken,
    finalizeMessage,
    updateThinking,
    clearThinking,
    openCitation,
    closeCitation,
    deleteConversation,
    fetchConversations,
  }
})
