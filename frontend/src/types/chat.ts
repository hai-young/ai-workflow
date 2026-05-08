// ── 对话消息 ──
export interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  thinking?: ThinkingProcess
  citations?: Citation[]
  safetyStatus?: 'pass' | 'warn' | 'block'
  isStreaming?: boolean
  createdAt?: string
}

// ── 思考过程 ──
export interface ThinkingStep {
  stage: string
  intent?: string
  rewritten_query?: string
  sub_queries?: string[]
  count?: number
  has_coreference?: boolean
}

export interface ThinkingProcess {
  intention?: string
  rewrittenQuery?: string
  subQueries?: string[]
  retrievedCount?: number
  rerankedCount?: number
  rerankStatus?: string
  safetyStatus?: string
  fallbacks?: string[]
  steps: ThinkingStep[]
  currentStage: string | null
}

// ── 引用 ──
export interface Citation {
  index: number
  docId: string
  fileName: string
  content: string
  relevanceScore: number
}

// ── 对话 ──
export interface Conversation {
  id: string
  title: string
  lastMessage: string
  updatedAt: string
  rounds?: number
  preview?: string
  lastRole?: string
}

// ── 对话详情（后端返回） ──
export interface ConversationDetail {
  sessionId: string
  history: HistoryEntry[]
}

export interface HistoryEntry {
  role: string
  content: string
}

// ── SSE 事件 ──
export interface ThinkingEventData {
  stage: string
  intent?: string
  rewritten_query?: string
  sub_queries?: string[]
  count?: number
  has_coreference?: boolean
}

export interface TokenEventData {
  token: string
}

export interface DoneEventData {
  answer: string
  citations?: Citation[]
  conversationId?: string
}

export interface ErrorEventData {
  error: string
}

// ── 请求体 ──
export interface AskRequest {
  question: string
  sessionId: string
  stream: boolean
}

// ── 非流式响应（兼容） ──
export interface AskResponse {
  success: boolean
  answer?: string
  thinking?: ThinkingProcess
  citations?: Citation[]
  conversationId?: string
  messageId?: string
  error?: string
}

// ── 对话列表响应 ──
export interface ConversationsResponse {
  success: boolean
  count: number
  sessions: SessionInfo[]
}

export interface SessionInfo {
  sessionId: string
  rounds: number
  preview: string
  lastRole: string
}

// ── 连接状态 ──
export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'error'
