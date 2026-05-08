import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import type { Document, IndexInfo, ConsistencyInfo, UploadItem, LogEntry } from '@/types/knowledge'
import {
  getIndexStatus, getDocuments as getDocsApi, checkConsistency as checkConsistencyApi,
  reindex as reindexApi, getErrorLogs as getErrorLogsApi, uploadDocument as uploadDocApi,
} from '@/api/rag'

export const useKnowledgeStore = defineStore('knowledge', () => {
  // ── State ──
  const indexStatus = reactive<{
    milvus: IndexInfo | null
    elasticsearch: IndexInfo | null
    consistency: ConsistencyInfo | null
  }>({ milvus: null, elasticsearch: null, consistency: null })
  const statusLoading = ref(false)

  const documents = ref<Document[]>([])
  const documentsLoading = ref(false)
  const documentsPagination = reactive({ page: 1, pageSize: 20, total: 0 })

  const uploadQueue = ref<UploadItem[]>([])
  const isUploading = ref(false)

  const errorLogs = ref<LogEntry[]>([])
  const errorLogPagination = reactive({ page: 1, pageSize: 20, total: 0 })
  const errorLogDrawerOpen = ref(false)

  // ── Actions ──

  async function fetchIndexStatus() {
    statusLoading.value = true
    try {
      const res = await getIndexStatus()
      if (res.success && res.data) {
        indexStatus.milvus = res.data.milvus
        indexStatus.elasticsearch = res.data.elasticsearch
        indexStatus.consistency = res.data.consistency
      }
    } finally {
      statusLoading.value = false
    }
  }

  async function fetchDocuments(params: { page?: number; pageSize?: number; keyword?: string; fileType?: string; indexStatus?: string } = {}) {
    documentsLoading.value = true
    try {
      const res = await getDocsApi(params)
      if (res.success && res.data) {
        documents.value = res.data.documents || []
        documentsPagination.total = res.data.total || 0
        documentsPagination.page = res.data.page || params.page || 1
        documentsPagination.pageSize = res.data.pageSize || params.pageSize || 20
      }
    } finally {
      documentsLoading.value = false
    }
  }

  async function uploadDocuments(files: File[]) {
    isUploading.value = true
    uploadQueue.value = files.map(f => ({
      id: crypto.randomUUID(),
      file: f,
      progress: 0,
      status: 'pending' as const,
    }))

    for (const item of uploadQueue.value) {
      item.status = 'uploading'
      try {
        const res = await uploadDocApi(item.file)
        item.progress = 100
        item.status = res.success ? 'done' : 'error'
        item.errorMessage = res.success ? undefined : (res as any).error
      } catch (e: any) {
        item.status = 'error'
        item.errorMessage = e?.message || '上传失败'
      }
    }
    isUploading.value = false
    await fetchDocuments({ page: 1, pageSize: documentsPagination.pageSize })
  }

  async function checkConsistencyCheck() {
    const res = await checkConsistencyApi()
    if (res.success) await fetchIndexStatus()
    return res
  }

  async function reindex(target: 'milvus' | 'elasticsearch' | 'all') {
    const res = await reindexApi(target)
    if (res.success) await fetchIndexStatus()
    return res
  }

  async function fetchErrorLogs(params: { page?: number; pageSize?: number; level?: string } = {}) {
    try {
      const res = await getErrorLogsApi(params)
      if (res.success) {
        errorLogs.value = res.logs as LogEntry[]
        errorLogPagination.total = res.total
      }
    } catch { /* ignored */ }
  }

  function toggleErrorLogDrawer() {
    errorLogDrawerOpen.value = !errorLogDrawerOpen.value
  }

  function clearUploadQueue() {
    uploadQueue.value = uploadQueue.value.filter(i => i.status !== 'done')
  }

  return {
    indexStatus, statusLoading,
    documents, documentsLoading, documentsPagination,
    uploadQueue, isUploading,
    errorLogs, errorLogPagination, errorLogDrawerOpen,
    fetchIndexStatus, fetchDocuments, uploadDocuments,
    checkConsistencyCheck, reindex, fetchErrorLogs, toggleErrorLogDrawer,
    clearUploadQueue,
  }
})
