// ── 索引状态 ──
export interface IndexInfo {
  name: string
  docCount: number
  status: 'healthy' | 'degraded' | 'offline'
  storeSize?: string
  collection?: string
}

export interface ConsistencyInfo {
  matched: number
  milvusOnly: number
  esOnly: number
}

export interface IndexStatus {
  milvus: IndexInfo
  elasticsearch: IndexInfo
  consistency: ConsistencyInfo
}

// ── 文档 ──
export interface Document {
  id: string
  docId: string
  fileName: string
  fileSize: string
  fileType: string
  uploadTime: string
  indexStatus: 'healthy' | 'partial' | 'failed'
  milvusStatus?: 'ok' | 'pending' | 'failed'
  esStatus?: 'ok' | 'pending' | 'failed'
  chunkCount?: number
}

export interface DocumentQuery {
  page?: number
  pageSize?: number
  keyword?: string
  fileType?: string
  indexStatus?: string
}

export interface DocumentListResponse {
  success: boolean
  documents: Document[]
  total: number
  page: number
  pageSize: number
}

// ── 上传 ──
export interface UploadItem {
  id: string
  file: File
  progress: number
  status: 'pending' | 'uploading' | 'done' | 'partial' | 'error'
  errorMessage?: string
}

// ── 错误日志 ──
export interface LogEntry {
  id: string
  timestamp: string
  level: 'error' | 'warn'
  message: string
  detail?: string
}

export interface LogQuery {
  page?: number
  pageSize?: number
  level?: string
  startTime?: string
  endTime?: string
}

export interface LogListResponse {
  success: boolean
  logs: LogEntry[]
  total: number
}

// ── 重建进度 ──
export interface ReindexProgress {
  taskId: string
  target: 'milvus' | 'elasticsearch' | 'all'
  status: 'running' | 'completed' | 'failed'
  milvusProgress: number
  milvusTotal: number
  esProgress: number
  esTotal: number
  retryQueue: RetryItem[]
}

export interface RetryItem {
  chunkId: string
  retryCount: number
  maxRetries: number
  status: 'waiting' | 'failed'
  nextRetryAt: string
}
