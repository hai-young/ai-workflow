<script setup lang="ts">
import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import { CloseOutlined, FileTextOutlined } from '@ant-design/icons-vue'

const chatStore = useChatStore()

const isOpen = computed(() => chatStore.citationPanel.isOpen)
const citations = computed(() => chatStore.citationPanel.citations)
const activeIndex = computed(() => chatStore.citationPanel.activeIndex)

function close() {
  chatStore.closeCitation()
}

function highlightCard(index: number) {
  chatStore.citationPanel.activeIndex = index
}

function scorePercent(score: number): number {
  return Math.round(score * 100)
}

function scoreColor(score: number): string {
  if (score >= 0.85) return '#22c55e'
  if (score >= 0.7) return '#3b82f6'
  if (score >= 0.5) return '#f59e0b'
  return '#ef4444'
}

// Listen for Escape
if (typeof window !== 'undefined') {
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && isOpen.value) close()
  })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="isOpen" class="citation-overlay" @click.self="close">
      <aside class="citation-panel" :class="{ open: isOpen }">
        <div class="panel-header">
          <div class="panel-title">
            <FileTextOutlined />
            <span>引用来源 ({{ citations.length }})</span>
          </div>
          <button class="close-btn" @click="close">
            <CloseOutlined />
          </button>
        </div>

        <div class="panel-body">
          <div
            v-for="c in citations"
            :key="c.index"
            class="citation-card"
            :class="{ active: c.index === activeIndex }"
            @click="highlightCard(c.index)"
          >
            <div class="card-header">
              <span class="card-index">[{{ c.index }}]</span>
              <span class="card-file">{{ c.fileName }}</span>
            </div>
            <p class="card-content">{{ c.content }}</p>
            <div class="card-footer">
              <div class="score-bar">
                <div class="score-label">相关度</div>
                <div class="score-track">
                  <div
                    class="score-fill"
                    :style="{
                      width: scorePercent(c.relevanceScore) + '%',
                      background: scoreColor(c.relevanceScore),
                    }"
                  ></div>
                </div>
                <span class="score-value" :style="{ color: scoreColor(c.relevanceScore) }">
                  {{ scorePercent(c.relevanceScore) }}%
                </span>
              </div>
            </div>
          </div>

          <div v-if="citations.length === 0" class="empty">
            暂无引用来源
          </div>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped lang="scss">
.citation-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: transparent;
}

.citation-panel {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  width: 340px;
  background: #15191f;
  border-left: 1px solid #252b33;
  display: flex;
  flex-direction: column;
  transform: translateX(100%);
  transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  z-index: 101;

  &.open {
    transform: translateX(0);
  }
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #252b33;
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #e8ecf1;
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #848e9c;
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: all 0.15s;

  &:hover {
    background: #1c2128;
    color: #e8ecf1;
  }
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.citation-card {
  padding: 14px 16px;
  border-radius: 8px;
  border: 1px solid #252b33;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #3b82f6;
    background: rgba(59, 130, 246, 0.04);
  }

  &.active {
    border-color: #3b82f6;
    background: rgba(59, 130, 246, 0.08);
    box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.2);
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.card-index {
  font-size: 12px;
  font-weight: 700;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.12);
  padding: 2px 6px;
  border-radius: 4px;
}

.card-file {
  font-size: 13px;
  font-weight: 600;
  color: #e8ecf1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-content {
  font-size: 13px;
  line-height: 1.6;
  color: #848e9c;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  align-items: center;
}

.score-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.score-label {
  font-size: 11px;
  color: #505968;
}

.score-track {
  flex: 1;
  height: 4px;
  background: #252b33;
  border-radius: 2px;
  overflow: hidden;
}

.score-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.score-value {
  font-size: 11px;
  font-weight: 600;
}

.empty {
  text-align: center;
  color: #505968;
  padding: 40px 0;
  font-size: 14px;
}
</style>
