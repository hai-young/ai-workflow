import request from '@/utils/request'
import type {
  ConversationsResponse,
  ConversationDetail,
  AskResponse,
} from '@/types/chat'
import type {
  DocumentQuery,
  DocumentListResponse,
  IndexStatus,
} from '@/types/knowledge'

// ── 对话管理 ──

export function getConversations() {
  return request.get<any, ConversationsResponse>('/rag/conversations')
}

export function getConversationDetail(sessionId: string) {
  return request.get<any, ConversationDetail>(`/rag/conversations/${sessionId}`)
}

export function deleteConversation(sessionId: string) {
  return request.delete<any, { success: boolean; message: string }>(`/rag/conversations/${sessionId}`)
}

// ── 非流式问答（兼容） ──

export function askSync(question: string, sessionId: string) {
  return request.post<any, AskResponse>('/rag/ask', { question, sessionId, stream: false })
}

// ── 文档上传 ──

export function uploadDocument(file: File) {
  const fd = new FormData()
  fd.append('file', file)
  // 不手动设置 Content-Type，让 axios 自动添加带 boundary 的 multipart/form-data
  return request.post<any, { success: boolean; message: string }>('/rag/upload', fd)
}

// ── 知识库管理 ──

export function getIndexStatus() {
  return request.get<any, { success: boolean; data: IndexStatus }>('/knowledge/index-status')
}

export function getDocuments(params: DocumentQuery) {
  return request.get<any, DocumentListResponse>('/knowledge/documents', { params })
}

export function checkConsistency() {
  return request.post<any, { success: boolean; data: any }>('/knowledge/consistency-check')
}

export function reindex(target: 'milvus' | 'elasticsearch' | 'all') {
  return request.post<any, { success: boolean; taskId?: string }>('/knowledge/reindex', { target })
}

export function getReindexStatus(taskId: string) {
  return request.get<any, { success: boolean; data: any }>(`/knowledge/reindex/status`, { params: { taskId } })
}

export function getErrorLogs(params: { page?: number; pageSize?: number; level?: string }) {
  return request.get<any, { success: boolean; logs: any[]; total: number }>('/knowledge/error-logs', { params })
}

export function getSuggestions() {
  return request.get<any, { success: boolean; suggestions: string[] }>('/rag/suggestions')
}

// ── 用户设置 ──

export function getUserSettings() {
  return request.get<any, { success: boolean; data: Record<string, any> }>('/user/settings')
}

export function updateUserSettings(settings: Record<string, any>) {
  return request.patch<any, { success: boolean; message: string }>('/user/settings', settings)
}

// ── 文档管理扩展 ──

export function deleteDocument(docId: string) {
  return request.delete<any, { success: boolean; message: string; deletedChunks: number }>(`/knowledge/documents/${docId}`)
}

export function getDocumentDetail(docId: string) {
  return request.get<any, { success: boolean; data: any }>(`/knowledge/documents/${docId}`)
}
