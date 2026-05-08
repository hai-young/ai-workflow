<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import {
  PlusOutlined, ReloadOutlined, WarningOutlined,
  CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined,
  FileTextOutlined, SearchOutlined, UploadOutlined, InboxOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import type { UploadFile } from 'ant-design-vue'
import ReindexProgressDrawer from '@/components/chat/ReindexProgressDrawer.vue'
import { deleteDocument } from '@/api/rag'

const store = useKnowledgeStore()

// ── State ──
const uploadModalOpen = ref(false)
const uploadFileList = ref<UploadFile[]>([])
const uploading = ref(false)

const reindexModalOpen = ref(false)
const reindexTarget = ref<'milvus' | 'elasticsearch' | 'all'>('all')
const reindexTaskId = ref<string | null>(null)
const reindexDrawerOpen = ref(false)

const searchKeyword = ref('')
const filterType = ref<string | undefined>(undefined)
const filterStatus = ref<string | undefined>(undefined)

const columns = [
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: '大小', dataIndex: 'fileSize', key: 'fileSize', width: 100 },
  { title: '上传时间', dataIndex: 'uploadTime', key: 'uploadTime', width: 160 },
  { title: '索引状态', key: 'indexStatus', width: 140 },
  { title: '操作', key: 'action', width: 130 },
]

// Current page for the table
const currentPage = ref(1)
const pageSize = ref(20)

onMounted(() => {
  store.fetchIndexStatus()
  store.fetchDocuments({ page: 1, pageSize: pageSize.value })
})

// ── Status helpers ──
function statusColor(status: string): string {
  switch (status) {
    case 'healthy': return '#22c55e'
    case 'degraded': return '#f59e0b'
    case 'offline': return '#ef4444'
    default: return '#505968'
  }
}

function docIndexStatusColor(status: string): string {
  switch (status) {
    case 'completed': return '#22c55e'
    case 'partial': return '#f59e0b'
    case 'pending': return '#ef4444'
    default: return '#505968'
  }
}

function docIndexStatusText(status: string, record: any): string {
  if (status === 'completed') return '已就绪'
  const parts: string[] = []
  if (record.esStatus === 'indexed') parts.push('ES')
  else parts.push('ES 未索引')
  if (record.milvusStatus === 'indexed') parts.push('Milvus')
  else parts.push('Milvus 未索引')
  return parts.join(' · ')
}

// ── Formatting ──
function formatFileSize(bytes: number | string): string {
  const n = typeof bytes === 'string' ? parseInt(bytes, 10) : bytes
  if (!n || n === 0) return '0 B'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / (1024 * 1024)).toFixed(2) + ' MB'
}

function formatTime(iso: string): string {
  if (!iso) return ''
  return iso.replace('T', ' ').substring(0, 19)
}

// ── Delete ──
const deleting = ref<string | null>(null)
async function handleDelete(docId: string, fileName: string) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除文档「${fileName}」吗？此操作将同时清理 ES、Milvus 和 MinIO 上的数据，不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      deleting.value = docId
      try {
        await deleteDocument(docId)
        message.success('删除成功')
        await store.fetchDocuments({ page: currentPage.value, pageSize: pageSize.value })
      } catch {
        message.error('删除失败')
      } finally {
        deleting.value = null
      }
    },
  })
}

// ── Upload ──
async function handleUpload() {
  const files = uploadFileList.value
    .filter(f => f.originFileObj)
    .map(f => f.originFileObj as File)
  if (files.length === 0) {
    message.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    await store.uploadDocuments(files)
    message.success('上传完成')
    uploadModalOpen.value = false
    uploadFileList.value = []
  } catch {
    message.error('上传失败')
  } finally {
    uploading.value = false
  }
}

// ── Reindex ──
function openReindexModal(target: 'milvus' | 'elasticsearch' | 'all') {
  reindexTarget.value = target
  reindexModalOpen.value = true
}

async function confirmReindex() {
  try {
    const res = await store.reindex(reindexTarget.value)
    reindexModalOpen.value = false
    if ((res as any).taskId) {
      reindexTaskId.value = (res as any).taskId
      reindexDrawerOpen.value = true
    }
    message.success('索引重建已启动')
  } catch {
    message.error('重建索引失败')
  }
}

function onReindexComplete() {
  reindexTaskId.value = null
  store.fetchIndexStatus()
  store.fetchDocuments({ page: 1, pageSize: pageSize.value })
}

// ── Search / Filter ──
function handleSearch() {
  currentPage.value = 1
  store.fetchDocuments({
    page: 1,
    pageSize: pageSize.value,
    keyword: searchKeyword.value || undefined,
    fileType: filterType.value,
    indexStatus: filterStatus.value,
  })
}

function handlePageChange(page: number) {
  currentPage.value = page
  store.fetchDocuments({
    page,
    pageSize: pageSize.value,
    keyword: searchKeyword.value || undefined,
    fileType: filterType.value,
    indexStatus: filterStatus.value,
  })
}

function handleRefresh() {
  store.fetchIndexStatus()
  store.fetchDocuments({ page: currentPage.value, pageSize: pageSize.value })
}

const checking = ref(false)
async function handleConsistencyCheck() {
  checking.value = true
  try {
    const res = await store.checkConsistencyCheck()
    if (res?.success) {
      message.success('一致性检查完成')
    } else {
      message.error('一致性检查失败')
    }
  } catch {
    message.error('一致性检查失败')
  } finally {
    checking.value = false
  }
}

function openErrorLogDrawer() {
  store.toggleErrorLogDrawer()
  if (store.errorLogDrawerOpen) {
    store.fetchErrorLogs({ page: 1, pageSize: 20 })
  }
}

const tableData = computed(() =>
  store.documents.map(d => ({ ...d, key: d.docId || d.id }))
)
</script>

<template>
  <div class="knowledge-page">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">知识库管理</h2>
      </div>
      <div class="header-actions">
        <button class="action-btn" @click="handleRefresh" title="刷新">
          <ReloadOutlined :spin="store.documentsLoading" />
        </button>
        <button class="action-btn primary" @click="uploadModalOpen = true">
          <UploadOutlined />
          <span>上传文档</span>
        </button>
      </div>
    </div>

    <div class="page-content">
      <!-- Index Status Cards -->
      <div class="status-cards">
        <!-- Milvus Card -->
        <div class="status-card">
          <div class="card-header">
            <span class="card-name">Milvus</span>
            <span class="card-dot" :style="{ background: statusColor(store.indexStatus.milvus?.status || '') }"></span>
          </div>
          <div class="card-body">
            <div class="card-stat">
              <span class="stat-label">文档数</span>
              <span class="stat-value">{{ store.indexStatus.milvus?.docCount ?? '--' }}</span>
            </div>
            <div class="card-stat">
              <span class="stat-label">集合</span>
              <span class="stat-value">{{ store.indexStatus.milvus?.collection ?? 'kb_base' }}</span>
            </div>
          </div>
        </div>

        <!-- ES Card -->
        <div class="status-card">
          <div class="card-header">
            <span class="card-name">Elasticsearch</span>
            <span class="card-dot" :style="{ background: statusColor(store.indexStatus.elasticsearch?.status || '') }"></span>
          </div>
          <div class="card-body">
            <div class="card-stat">
              <span class="stat-label">文档数</span>
              <span class="stat-value">{{ store.indexStatus.elasticsearch?.docCount ?? '--' }}</span>
            </div>
            <div class="card-stat">
              <span class="stat-label">Chunk 数</span>
              <span class="stat-value">{{ store.indexStatus.elasticsearch?.storeSize ?? '--' }}</span>
            </div>
          </div>
        </div>

        <!-- Consistency Card -->
        <div class="status-card">
          <div class="card-header">
            <span class="card-name">一致性</span>
            <span
              class="card-dot"
              :style="{
                background: store.indexStatus.consistency
                  ? (store.indexStatus.consistency.milvusOnly + store.indexStatus.consistency.esOnly) === 0
                    ? '#22c55e' : '#f59e0b'
                  : '#505968'
              }"
            ></span>
          </div>
          <div class="card-body">
            <div class="card-stat">
              <span class="stat-label">匹配</span>
              <span class="stat-value" style="color: #22c55e">{{ store.indexStatus.consistency?.matched ?? '--' }}</span>
            </div>
            <div class="card-stat">
              <span class="stat-label">差异</span>
              <span class="stat-value" :style="{ color: (store.indexStatus.consistency?.milvusOnly ?? 0) + (store.indexStatus.consistency?.esOnly ?? 0) > 0 ? '#f59e0b' : '#22c55e' }">
                {{ (store.indexStatus.consistency?.milvusOnly ?? 0) + (store.indexStatus.consistency?.esOnly ?? 0) }}
              </span>
            </div>
          </div>
          <div class="card-actions">
            <button class="card-btn" @click="handleConsistencyCheck" :disabled="checking">{{ checking ? '检查中...' : '检查' }}</button>
            <button class="card-btn warn" @click="openReindexModal('all')">重建</button>
          </div>
        </div>
      </div>

      <!-- Toolbar -->
      <div class="table-toolbar">
        <div class="toolbar-left">
          <a-input-search
            v-model:value="searchKeyword"
            placeholder="搜索文件名..."
            style="width: 260px"
            @search="handleSearch"
          />
          <a-select
            v-model:value="filterType"
            placeholder="文件类型"
            style="width: 130px"
            allow-clear
            @change="handleSearch"
          >
            <a-select-option value="pdf">PDF</a-select-option>
            <a-select-option value="docx">DOCX</a-select-option>
            <a-select-option value="md">Markdown</a-select-option>
            <a-select-option value="txt">TXT</a-select-option>
          </a-select>
          <a-select
            v-model:value="filterStatus"
            placeholder="索引状态"
            style="width: 130px"
            allow-clear
            @change="handleSearch"
          >
            <a-select-option value="completed">已就绪</a-select-option>
            <a-select-option value="partial">部分就绪</a-select-option>
            <a-select-option value="pending">待索引</a-select-option>
          </a-select>
        </div>
        <div class="toolbar-right">
          <button class="action-btn" @click="openErrorLogDrawer">
            <WarningOutlined />
            <span>错误日志</span>
          </button>
        </div>
      </div>

      <!-- Document Table -->
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="store.documentsLoading"
        :pagination="{
          current: currentPage,
          pageSize: pageSize,
          total: store.documentsPagination.total,
          showSizeChanger: true,
          showTotal: (total: number) => `共 ${total} 条`,
        }"
        @change="(pag: any) => handlePageChange(pag.current)"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'fileName'">
            <div class="file-cell">
              <FileTextOutlined class="file-icon" />
              <span>{{ record.fileName }}</span>
            </div>
          </template>
          <template v-if="column.key === 'fileSize'">
            <span>{{ formatFileSize(record.fileSize) }}</span>
          </template>
          <template v-if="column.key === 'uploadTime'">
            <span>{{ formatTime(record.uploadTime) }}</span>
          </template>
          <template v-if="column.key === 'indexStatus'">
            <div class="status-cell">
              <span
                class="status-dot"
                :style="{ background: docIndexStatusColor(record.indexStatus) }"
              ></span>
              <span :style="{ color: docIndexStatusColor(record.indexStatus) }">
                {{ docIndexStatusText(record.indexStatus, record) }}
              </span>
            </div>
          </template>
          <template v-if="column.key === 'action'">
            <div class="action-cell">
              <a-button
                v-if="record.indexStatus !== 'completed'"
                type="link"
                size="small"
                @click="openReindexModal('all')"
              >
                重建
              </a-button>
              <a-button
                type="link"
                size="small"
                danger
                :loading="deleting === record.docId"
                @click="handleDelete(record.docId, record.fileName)"
              >
                <DeleteOutlined />
              </a-button>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- Upload Modal -->
    <a-modal
      v-model:open="uploadModalOpen"
      title="上传文档"
      @ok="handleUpload"
      :confirm-loading="uploading"
      ok-text="上传"
      cancel-text="取消"
    >
      <a-upload-dragger
        v-model:file-list="uploadFileList"
        :multiple="true"
        :before-upload="() => false"
        accept=".pdf,.doc,.docx,.txt,.md"
      >
        <p class="upload-icon">
          <InboxOutlined style="font-size: 48px; color: #3b82f6" />
        </p>
        <p class="upload-text">点击或拖拽文件到此区域上传</p>
        <p class="upload-hint">支持 PDF、DOC、DOCX、TXT、MD 格式，单文件最大 50MB</p>
      </a-upload-dragger>
    </a-modal>

    <!-- Reindex Modal -->
    <a-modal
      v-model:open="reindexModalOpen"
      title="确认重建索引"
      @ok="confirmReindex"
      ok-text="确认重建"
      cancel-text="取消"
      ok-button-props="{ danger: true }"
    >
      <p>确定要重建索引吗？</p>
      <p class="reindex-target">目标：{{ reindexTarget === 'all' ? '全部 (Milvus + ES)' : reindexTarget }}</p>
      <p class="reindex-warning">重建期间检索功能可能受影响，建议在低峰期操作。</p>
    </a-modal>

    <!-- Error Log Drawer -->
    <a-drawer
      :open="store.errorLogDrawerOpen"
      title="错误日志"
      placement="right"
      width="420"
      @close="store.toggleErrorLogDrawer()"
    >
      <div v-if="store.errorLogs.length === 0" class="empty-logs">
        暂无错误日志
      </div>
      <div v-for="log in store.errorLogs" :key="log.id" class="log-item" :class="log.level">
        <div class="log-header">
          <span class="log-level">{{ log.level === 'error' ? '✗ 错误' : '⚠ 警告' }}</span>
          <span class="log-time">{{ log.timestamp }}</span>
        </div>
        <div class="log-message">{{ log.message }}</div>
        <div v-if="log.detail" class="log-detail">{{ log.detail }}</div>
      </div>
      <a-pagination
        v-if="store.errorLogPagination.total > store.errorLogPagination.pageSize"
        :current="store.errorLogPagination.page"
        :page-size="store.errorLogPagination.pageSize"
        :total="store.errorLogPagination.total"
        size="small"
        @change="(p: number) => store.fetchErrorLogs({ page: p, pageSize: store.errorLogPagination.pageSize })"
      />
    </a-drawer>

    <!-- Reindex Progress Drawer -->
    <ReindexProgressDrawer
      :open="reindexDrawerOpen"
      :task-id="reindexTaskId"
      :target="reindexTarget"
      @close="reindexDrawerOpen = false"
      @complete="onReindexComplete"
    />
  </div>
</template>

<style scoped lang="scss">
.knowledge-page {
  height: calc(100vh - 60px);
  overflow-y: auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  height: 64px;
  border-bottom: 1px solid #252b33;
  flex-shrink: 0;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #e8ecf1;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid #252b33;
  border-radius: 8px;
  background: transparent;
  color: #e8ecf1;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;

  &:hover {
    border-color: #3b82f6;
    background: rgba(59, 130, 246, 0.06);
  }

  &.primary {
    background: linear-gradient(135deg, #3b82f6, #8b5cf6);
    border: none;
    color: white;

    &:hover {
      box-shadow: 0 0 16px rgba(59, 130, 246, 0.4);
    }
  }
}

.page-content {
  padding: 24px 32px;
}

// Status Cards
.status-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.status-card {
  background: #15191f;
  border: 1px solid #252b33;
  border-radius: 10px;
  padding: 18px 20px;

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
  }

  .card-name {
    font-size: 14px;
    font-weight: 600;
    color: #e8ecf1;
  }

  .card-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  .card-body {
    display: flex;
    gap: 24px;
    margin-bottom: 14px;
  }

  .card-stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .stat-label {
    font-size: 11px;
    color: #505968;
  }

  .stat-value {
    font-size: 18px;
    font-weight: 600;
    color: #e8ecf1;
    font-family: 'JetBrains Mono', monospace;
  }

  .card-actions {
    display: flex;
    gap: 8px;
  }

  .card-btn {
    padding: 6px 14px;
    border: 1px solid #252b33;
    border-radius: 6px;
    background: transparent;
    color: #e8ecf1;
    cursor: pointer;
    font-size: 12px;
    transition: all 0.15s;

    &:hover {
      border-color: #3b82f6;
    }

    &.warn {
      border-color: rgba(239, 68, 68, 0.25);
      color: #ef4444;

      &:hover {
        background: rgba(239, 68, 68, 0.08);
        border-color: #ef4444;
      }
    }
  }
}

// Toolbar
.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .toolbar-left {
    display: flex;
    gap: 12px;
    align-items: center;
  }
}

// Table
.file-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #e8ecf1;

  .file-icon {
    color: #505968;
  }
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }
}

.action-cell {
  display: flex;
  gap: 4px;
}

// Upload
.upload-icon {
  margin: 8px 0 0;
}

.upload-text {
  font-size: 15px;
  color: #e8ecf1;
  margin: 8px 0 4px;
}

.upload-hint {
  font-size: 12px;
  color: #505968;
  margin: 0;
}

// Reindex
.reindex-target {
  font-weight: 600;
  color: #3b82f6;
}

.reindex-warning {
  font-size: 13px;
  color: #f59e0b;
}

// Error logs
.empty-logs {
  text-align: center;
  color: #505968;
  padding: 40px;
}

.log-item {
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 8px;
  border-left: 3px solid #505968;
  background: #15191f;

  &.error {
    border-left-color: #ef4444;
  }
  &.warn {
    border-left-color: #f59e0b;
  }
}

.log-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12px;

  .log-level {
    font-weight: 600;
  }
  .log-time {
    color: #505968;
  }
}

.log-message {
  font-size: 13px;
  color: #e8ecf1;
  margin-bottom: 4px;
}

.log-detail {
  font-size: 12px;
  color: #505968;
  background: #0b0e11;
  padding: 8px;
  border-radius: 4px;
  white-space: pre-wrap;
}
</style>
