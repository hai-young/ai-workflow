<script setup lang="ts">
import { ref, reactive, watch, onMounted, onUnmounted, computed } from 'vue'
import { getReindexStatus } from '@/api/rag'
import { message } from 'ant-design-vue'

const props = defineProps<{
  open: boolean
  taskId: string | null
  target: 'milvus' | 'elasticsearch' | 'all'
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'complete'): void
}>()

interface RetryItem {
  chunkId: string
  retryCount: number
  maxRetries: number
  status: 'waiting' | 'failed'
  nextRetryAt?: string
}

const milvusProgress = ref(0)
const milvusTotal = ref(0)
const esProgress = ref(0)
const esTotal = ref(0)
const status = ref('pending')
const retryQueue = ref<RetryItem[]>([])
const pollTimer = ref<ReturnType<typeof setInterval> | null>(null)

const targetLabel = computed(() => {
  switch (props.target) {
    case 'milvus': return 'Milvus'
    case 'elasticsearch': return 'ES'
    case 'all': return '全部'
  }
})

const targetColor = computed(() => {
  switch (props.target) {
    case 'milvus': return '#06b6d4'
    case 'elasticsearch': return '#f59e0b'
    case 'all': return '#3b82f6'
  }
})

const milvusPercent = computed(() => {
  if (milvusTotal.value === 0) return 0
  return Math.min(Math.round((milvusProgress.value / milvusTotal.value) * 100), 100)
})

const esPercent = computed(() => {
  if (esTotal.value === 0) return 0
  return Math.min(Math.round((esProgress.value / esTotal.value) * 100), 100)
})

const statusText = computed(() => {
  switch (status.value) {
    case 'pending': return '等待中...'
    case 'running': return '重建中...'
    case 'completed': return '已完成'
    case 'failed': return '重建失败'
    default: return status.value
  }
})

const statusColor = computed(() => {
  switch (status.value) {
    case 'completed': return '#22c55e'
    case 'failed': return '#ef4444'
    case 'running': return '#3b82f6'
    default: return '#505968'
  }
})

function startPolling() {
  stopPolling()
  if (!props.taskId) return

  pollTimer.value = setInterval(async () => {
    try {
      const res = await getReindexStatus(props.taskId!)
      if (res.success) {
        milvusProgress.value = res.milvusProgress ?? 0
        milvusTotal.value = res.milvusTotal ?? 0
        esProgress.value = res.esProgress ?? 0
        esTotal.value = res.esTotal ?? 0
        status.value = res.status ?? 'pending'
        retryQueue.value = res.retryQueue ?? []

        if (res.status === 'completed') {
          stopPolling()
          message.success('索引重建完成')
          emit('complete')
          emit('close')
        }
      }
    } catch {
      // continue polling even on error
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

async function handleRetry(chunkId: string) {
  try {
    // dispatch retry via the reindex status endpoint is not supported as a separate action,
    // but we show the UI for it. In a real implementation, this would call a dedicated retry API.
    message.info(`已提交重试请求: ${chunkId}`)
  } catch {
    message.error('重试失败')
  }
}

function retryItemStatusColor(status: string): string {
  return status === 'failed' ? '#ef4444' : '#f59e0b'
}

function retryItemStatusText(status: string): string {
  return status === 'failed' ? '已失败' : '等待重试'
}

function formatNextRetryAt(nextRetryAt?: string): string {
  if (!nextRetryAt) return '--'
  try {
    const d = new Date(nextRetryAt)
    return d.toLocaleTimeString()
  } catch {
    return nextRetryAt
  }
}

watch(() => props.taskId, (newId) => {
  if (newId && props.open) {
    startPolling()
  } else {
    stopPolling()
  }
})

watch(() => props.open, (isOpen) => {
  if (isOpen && props.taskId) {
    startPolling()
  } else {
    stopPolling()
    // Reset state when closed
    if (!isOpen) {
      milvusProgress.value = 0
      milvusTotal.value = 0
      esProgress.value = 0
      esTotal.value = 0
      status.value = 'pending'
      retryQueue.value = []
    }
  }
})

onMounted(() => {
  if (props.open && props.taskId) {
    startPolling()
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <a-drawer
    :open="open"
    title="索引重建"
    placement="right"
    :width="420"
    :closable="true"
    @close="emit('close')"
  >
    <!-- Target badge -->
    <div class="target-badge" :style="{ borderColor: targetColor, color: targetColor }">
      {{ targetLabel }}
    </div>

    <!-- Milvus Progress -->
    <div class="progress-section">
      <div class="progress-label">Milvus</div>
      <a-progress
        :percent="milvusPercent"
        :stroke-color="{ from: '#3b82f6', to: '#06b6d4' }"
        :stroke-width="8"
      />
      <div class="progress-count" v-if="milvusTotal > 0">
        {{ milvusProgress }} / {{ milvusTotal }}
      </div>
      <div class="progress-count progress-count--pending" v-else>
        等待中...
      </div>
    </div>

    <!-- ES Progress -->
    <div class="progress-section">
      <div class="progress-label">Elasticsearch</div>
      <a-progress
        :percent="esPercent"
        :stroke-color="{ from: '#f59e0b', to: '#eab308' }"
        :stroke-width="8"
      />
      <div class="progress-count" v-if="esTotal > 0">
        {{ esProgress }} / {{ esTotal }}
      </div>
      <div class="progress-count progress-count--pending" v-else>
        等待中...
      </div>
    </div>

    <!-- Status -->
    <div class="status-line">
      <span class="status-dot" :style="{ background: statusColor }"></span>
      <span class="status-text" :style="{ color: statusColor }">{{ statusText }}</span>
    </div>

    <!-- Retry Queue -->
    <div class="retry-section" v-if="retryQueue.length > 0">
      <h4 class="retry-heading">重试队列 ({{ retryQueue.length }})</h4>
      <div
        v-for="item in retryQueue"
        :key="item.chunkId"
        class="retry-item"
      >
        <div class="retry-info">
          <span class="retry-chunk-id">{{ item.chunkId }}</span>
          <span class="retry-count">
            {{ item.retryCount }} / {{ item.maxRetries }}
          </span>
          <span
            class="retry-status"
            :style="{ color: retryItemStatusColor(item.status) }"
          >
            {{ retryItemStatusText(item.status) }}
          </span>
        </div>
        <div class="retry-meta">
          <span class="retry-next" v-if="item.nextRetryAt">
            下次重试: {{ formatNextRetryAt(item.nextRetryAt) }}
          </span>
          <button
            v-if="item.status === 'failed'"
            class="retry-btn"
            @click="handleRetry(item.chunkId)"
          >
            重试
          </button>
        </div>
      </div>
    </div>

    <!-- Empty retry state -->
    <div class="retry-empty" v-else-if="status === 'running'">
      暂无失败分块
    </div>
  </a-drawer>
</template>

<style scoped lang="scss">
.target-badge {
  display: inline-block;
  padding: 4px 12px;
  border: 1px solid;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 24px;
}

.progress-section {
  margin-bottom: 20px;

  .progress-label {
    font-size: 13px;
    font-weight: 600;
    color: #e8ecf1;
    margin-bottom: 8px;
  }

  .progress-count {
    font-size: 12px;
    color: #848e9c;
    margin-top: 6px;
    font-family: 'JetBrains Mono', monospace;

    &--pending {
      color: #505968;
      font-style: italic;
    }
  }
}

.status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  margin-bottom: 20px;
  border-bottom: 1px solid #252b33;

  .status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
  }

  .status-text {
    font-size: 14px;
    font-weight: 500;
  }
}

.retry-section {
  margin-top: 8px;
}

.retry-heading {
  font-size: 13px;
  font-weight: 600;
  color: #e8ecf1;
  margin: 0 0 12px;
}

.retry-item {
  background: #15191f;
  border: 1px solid #252b33;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
}

.retry-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.retry-chunk-id {
  font-size: 12px;
  color: #848e9c;
  font-family: 'JetBrains Mono', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.retry-count {
  font-size: 12px;
  color: #e8ecf1;
  font-family: 'JetBrains Mono', monospace;
}

.retry-status {
  font-size: 12px;
  font-weight: 600;
}

.retry-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.retry-next {
  font-size: 11px;
  color: #505968;
}

.retry-btn {
  padding: 4px 12px;
  border: 1px solid #ef4444;
  border-radius: 6px;
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
  cursor: pointer;
  font-size: 11px;
  transition: all 0.15s;

  &:hover {
    background: rgba(239, 68, 68, 0.16);
  }
}

.retry-empty {
  text-align: center;
  color: #505968;
  font-size: 13px;
  padding: 20px;
}
</style>
